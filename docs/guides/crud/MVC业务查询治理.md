# MVC 业务查询治理

## 自定义 Controller 优先复用 CRUD 内核

自定义 HTTP 接口不要求自行编写查询实现。普通实体查询与写入直接调用 `QueryGateway` / `CommandGateway`：Controller 使用 `@EntAccess` 建立可信访问作用域，组装 `QuerySpec` / `CommandSpec`，由内核统一完成授权、数据范围、字段校验、持久化与审计。

```java
var spec = QuerySpec.builder(MerchantArticle.class, MerchantArticleSummary.class)
    .page(new PageRequest(query.page(), query.limit())).build();
return CrudResponse.success(queryGateway.page(spec));
```

实体可声明 `@EntCrudEntity(httpExposed = false)` 关闭通用 HTTP，只保留自定义 Controller。该设置不关闭内核访问能力，权限继续由实体授权声明或策略控制。默认实体查询使用 `QuerySpec.builder(MerchantArticle.class)`；固定投影使用 DTO，内核按其属性推导查询字段并检查完整授权。创建调用 `commandGateway.create(...)`，部分更新接收 `UpdatePatch<MerchantArticle>` 并调用 `commandGateway.update(...)`，写入返回 `EntityWriteResult<Long>`。详见[实体优先的类型化 CRUD](实体优先的类型化CRUD.md)。无需自行解析授权范围，也无需嵌套 `BusinessQueryExecutor` 重复授权和审计。

可运行示例：[文章 Controller](../../../examples/mini-commerce-spring-boot/src/main/java/com/example/minicommerce/merchant/web/MerchantArticleController.java)、[HTTP 集成测试](../../../examples/mini-commerce-spring-boot/src/test/java/com/example/minicommerce/merchant/MerchantArticleIntegrationTest.java)；完整对照见[两种 CRUD 接入演示](../../../examples/mini-commerce-spring-boot/docs/两种CRUD接入演示.md)。

## 原始业务 SQL 的治理入口

确实需要自行编写 SQL、统计或特殊 DTO 查询时，通过 Core 的 `BusinessQueryExecutor` 执行。Starter 在业务授权服务和审计记录器可用时自动注册该入口；不依赖 HTTP、CRUD Spec 或 JDBC。

```java
public PageResult<SummaryDto> page(VisitorContext visitor, Query query) {
    return businessQueryExecutor.execute(
        new AuthorizationRequest(visitor, VIEW, null),
        authorization -> preparePage(authorization, query),
        this::loadPage
    );
}
```

## 职责

分页响应复用 `com.entloom.crud.api.model.PageResult<T>`，业务投影放在业务 `dto` 包。Mapper、实体服务和查询服务共同引用 DTO，不依赖查询服务的内部响应类型。

| 环节 | 责任 |
| --- | --- |
| 框架授权 | 每次调用同一个 `BusinessAuthorizationService`，检查允许结果并校验本次请求绑定；不接受外部授权结果 |
| 业务准备 | 声明实际字段用途，将授权范围转换为查询专用参数；不执行查询 SQL，必须返回非空准备结果 |
| 业务执行 | 用准备结果调用原始 Mapper，落实数据、计数、关联与输出约束 |
| 框架收尾 | 记录结果、阶段、原因、耗时及请求链路；隔离审计故障，不覆盖查询返回值或原异常 |

字段检查复用 `AuthorizationFields.requireAllowed`，业务不再重复实现允许判断和异常抛出：

```java
var fields = authorization.getFields();
fields.requireAllowed(AuthorizationFields.Use.RETURN, "id", "name", "price");
fields.requireAllowed(AuthorizationFields.Use.SORT, "id");
if (query.active() != null) {
    fields.requireAllowed(AuthorizationFields.Use.FILTER, "active");
}
```

授权策略仍通过实体授权声明或业务 Policy 表达。服务准备阶段只适配本次授权结果，不另建权限判断。

## 范围与执行边界

- `ALL`、`NONE`、受限范围明确区分；`NONE` 返回空结果，不访问数据库。
- 仅支持能够正确落到 SQL 的范围；不支持的范围在准备阶段拒绝，不能省略条件或降级为全量。
- 准备参数使用业务自己的类型；例如将分页参数、范围类型和归属 ID 组成私有 `PagePlan`。
- 统一入口不能自动证明原始 SQL 正确；列表与 count 应复用相同范围片段，并用集成测试验证。
- 两阶段同步完成，不保存授权结果供其他调用复用，不返回异步任务或延迟执行查询的对象。
- 本入口负责整次查询审计；回调使用原始 Mapper，不再嵌套已经自行审计的 CRUD、受授权查询执行器或授权 DAO。
- 写入继续走具备事务提交后审计语义的命令入口。

## 审计结果

| 情况 | outcome | stage / reason |
| --- | --- | --- |
| 授权或准备约束拒绝 | `GOVERNANCE_DENIED` | `GOVERNANCE` / 对应框架错误码 |
| 目标不可访问 | `GOVERNANCE_DENIED` | `GOVERNANCE` / `DATA_SCOPE_DENIED`；查询对外返回不存在 |
| 授权或准备内部错误 | `GOVERNANCE_FAILED` | `GOVERNANCE` / 原框架故障码或 `INTERNAL_ERROR` |
| 查询异常 | `EXECUTION_FAILED` | `EXECUTE` / 对应框架错误码或 `EXECUTION_FAILED` |
| 查询成功，包括空结果 | `SUCCESS` | 无失败阶段 / `NONE` |

治理拒绝只识别明确的权限、范围和输入约束错误码，其他框架服务故障记为 `GOVERNANCE_FAILED`。

资源、操作和 routeKey 自动从授权请求推导，routeKey 为 `资源.业务操作`。事件独立保留原始授权请求，授权提前拒绝或故障时也能追溯调用 ID 与目标 ID；JDBC 审计 JSON 的 `request` 保存请求信息，`authorization` 仅在有授权结果时保存决策、范围与字段。公共分支的实际认证主体仅从同次准入提取用于审计，不重新参与授权。审计失败记录统一日志；存在业务异常时附加为 suppressed 异常，不尝试重新执行查询。

原始 SQL 入口的授权、阶段与审计合同见 [BusinessQueryExecutorTest](../../../ent-loom-modules/ent-loom-crud/ent-loom-crud-core/src/test/java/com/entloom/crud/core/governance/execution/BusinessQueryExecutorTest.java)。
