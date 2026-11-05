# 实体优先的类型化 CRUD

普通 Java 业务使用明确类型：完整查询返回实体，固定投影返回 DTO，局部更新使用 `UpdatePatch<T>`。`CrudRecord` 保留在通用 HTTP 动态字段、统计、导出和低阶执行边界，不作为业务默认返回类型。

## 实体与投影查询

```java
// 根实体和结果类型由同一个 Class 推导。
var query = QuerySpec.builder(MerchantArticle.class)
    .page(new PageRequest(1, 20)).build();
PageResult<MerchantArticle> articles = queryGateway.page(query);

// DTO 属性自动声明所需的普通字段，不再重复维护字符串投影清单。
var summary = QuerySpec.builder(MerchantArticle.class, MerchantArticleSummary.class)
    .page(new PageRequest(1, 20)).build();
PageResult<MerchantArticleSummary> summaries = queryGateway.page(summary);
```

DTO 的属性名称对应根实体字段，支持普通 JavaBean、具有参数名的构造器和 Java record。实体与主键定位、筛选、排序、计数仍遵循同一 `QuerySpec` 合同；查询网关和显式 `AuthorizedQueryExecutor` 均保留泛型结果，不再通过强制转换伪装返回类型。

## 字段权限与关系

类型化结果必须完整授权其全部普通属性。权限不足、未知属性或显式投影遗漏必要属性时，在 SQL 前拒绝。不能将未授权字段简单赋 null 后序列化为完整实体；应选择字段更少的 DTO。

关系继续由 `expandRelations` 显式请求和 `EXPAND` 权限控制；一级关联可以映射为明确子实体或子 DTO，子结果也必须完整授权。未展开关系置空，不沿用实体构造器中的默认关联对象。连接键只用于内部补数，不因查询需要而扩大返回字段。

需要按身份动态变化的字段集合时，显式选择 `CrudRecord.class` 或 `Map.class`。通用 HTTP 的默认 `MAP` 模式正属于这种动态契约，继续只序列化实际授权字段；显式 `ENTITY` 或注册 DTO 同样遵守完整字段授权。

## 创建、局部更新与删除

```java
private static final EntityType<MerchantArticle, Long> ARTICLES =
    EntityType.of(MerchantArticle.class, Long.class);

EntityWriteResult<Long> created = commandGateway.create(ARTICLES, article);
EntityWriteResult<Long> updated = commandGateway.update(ARTICLES, id, patch);
EntityWriteResult<Long> deleted = commandGateway.delete(ARTICLES, id);
```

创建接收实体，由内核校验可写字段、填充可信归属、应用创建默认值和生成主键。实体未初始化的框架管理字段允许保持 null；显式非空的主键、归属、版本等字段仍接受原有校验。创建也可通过 `CommandSpec` 传入专用 DTO，输入属性必须对应实体字段。

`UpdatePatch<T>` 表达部分更新，不以实体字段是否为 null 推断提交情况。Starter 支持直接在自定义 Controller 绑定：

```java
@PutMapping("/{id}")
public CrudResponse<EntityWriteResult<Long>> update(
    @PathVariable("id") long id,
    @RequestBody UpdatePatch<MerchantArticle> patch
) {
    return CrudResponse.success(commandGateway.update(ARTICLES, id, patch));
}
```

`{"title":"新标题"}` 只修改标题；`{"title":null}` 表示显式清空，继续接受实体约束校验。未知属性在绑定时拒绝，主键目标、归属、只读字段、版本和可写权限由内核检查。空 Patch 拒绝，不静默执行空更新。

Java 业务代码使用 Builder 显式构造局部更新，无需维护内部字段集合或 delegate Map：

```java
var patch = UpdatePatch.builder(MerchantArticle.class, id)
    .set("title", "新标题")
    .build();
commandGateway.update(ARTICLES, id, patch);
```

`set` 表示显式提交字段，允许传入 null；未调用 `set` 的字段保持不变。`build()` 返回不可变快照，空 Patch 拒绝。启用乐观锁的实体使用 `.expectedVersion(version)` 传入预期版本，不通过 `set("version", ...)` 修改版本。Builder 不授予字段权限，也不替代字段识别、值绑定、实体约束及 DAO 版本检查。

类型化命令保留 `EntityType` 的主键类型，并在 SQL 执行前与实体元数据核对；错误描述符不会先写入再报结果类型错误，基本类型与对应包装类型视为一致。

写入结果使用 `EntityWriteResult<ID>`：`rows` 为影响行数，创建的 `id` 为生成主键。启用乐观锁的删除使用 `delete(type, id, expectedVersion)`。

这些入口复用现有业务授权、SQL 安全守卫、事务和审计；自定义 Controller 仍通过 `@EntAccess` 建立可信访问作用域。

## 示例

[MerchantArticleController](../../../examples/mini-commerce-spring-boot/src/main/java/com/example/minicommerce/merchant/web/MerchantArticleController.java) 演示实体详情、摘要 DTO、创建输入 DTO 和类型化局部更新；[两种 CRUD 接入演示](../../../examples/mini-commerce-spring-boot/docs/两种CRUD接入演示.md) 对照自定义 Controller 与通用 Controller。

## 业务动作授权与固定更新

业务命令服务通过 `access.requireAction(CommerceOrderActions.CANCEL_ORDER)` 校验当前授权。框架核对当前可信访问作用域、动作实体、实体元数据资源码与动作操作码，业务无需遍历授权内部对象或硬编码资源码。动作契约沿用 Controller 与 Handler 注册共用的 `CommandAction<P, R>`。

```java
access.requireAction(CommerceOrderActions.CANCEL_ORDER);
var orders = access.dao(daoFactory, ORDER_TYPE,
    RowConstraint.eq("status", CommerceOrderStatus.CREATED));
var patch = UpdatePatch.builder(CommerceOrder.class, id)
    .set("status", CommerceOrderStatus.CANCELLED).build();
int rows = orders.updateById(id, patch);
```

动作校验不替代数据范围约束；DAO 仍将当前授权范围和固定业务条件合并到 SQL。取消订单保留原状态条件，防止并发状态变化被覆盖，并由业务处理未命中的更新结果。
