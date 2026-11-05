# CRUD 与业务动作接入演示

文章、商品与订单分别展示不同接入方式，均使用同一 CRUD 内核处理授权与持久化。Java 业务默认使用实体，固定投影使用 DTO；通用 HTTP 的动态字段结果保留动态记录。类型合同见[实体优先的类型化 CRUD](../../../docs/guides/crud/实体优先的类型化CRUD.md)。

| 关注点 | 商户文章 MerchantArticle | 商家商品 MerchantProduct |
| --- | --- | --- |
| Controller | 自定义 MerchantArticleController | 框架通用 Controller |
| 通用 HTTP 暴露 | `httpExposed = false` | 默认开放 |
| 请求契约 | 自定义路由；写入接受扁平字段对象 | 标准 `options` / `payload` |
| 内核入口 | QueryGateway 的实体 / DTO 查询与 CommandGateway 的类型化写入 | 通用 Controller 经 Facade 调用内核 |
| 业务权限 | 实体授权声明 + 全局归属约定 | 实体授权声明 + 全局归属约定 |
| 数据访问 | 内核执行，无业务 Mapper / SQL | 内核执行，无商品专用查询代码 |
| 请求文件 | [article.http](../requests/article.http) | [merchant.http](../requests/merchant.http) |

## 文章：定制 Controller，复用内核

[MerchantArticle](../src/main/java/com/example/minicommerce/merchant/entity/MerchantArticle.java) 声明：

```java
@EntCrudGrant(identities = "merchant", actions = {"view", "create", "update", "delete"})
@EntCrudEntity(httpExposed = false)
```

商家归属字段复用 `grant-defaults.owner-fields.merchant: merchantId`，标题和正文通过实体 `@NotBlank` 校验。关闭通用 HTTP 后，实体仍可由内部网关执行，不需要重新编写数据权限或 DAO。

[MerchantArticleController](../src/main/java/com/example/minicommerce/merchant/web/MerchantArticleController.java) 使用 `@EntAccess(accessEntries = "merchant")` 建立可信访问作用域，并将自定义参数转换为内核 Spec：

```java
var spec = QuerySpec.builder(MerchantArticle.class, MerchantArticleSummary.class)
    .sorts(List.of(new QuerySort("id", SortDirection.ASC)))
    .page(query.toPageRequest()).build();
return CrudResponse.success(queryGateway.page(spec));
```

网关从可信作用域取得访问者，内核执行授权、商家范围与分页计数。Controller 无需注入访问者、自行解析 `AuthorizationScope` 或为计数重复编写范围 SQL。

无筛选条件的业务分页接口可直接使用公共 `PageInput`：默认第 1 页、每页 20 条，最大 100 条；有业务筛选条件时可在请求 DTO 中组合并通过 `@Valid` 级联校验。应用统一启用 Jackson 未知字段拒绝，业务 DTO 不必逐个声明拒绝方法。

| 接口 | 契约与行为 |
| --- | --- |
| `POST /api/merchant/articles/page` | `{"page":1,"limit":20}`；只返回 `id/title`，分页信息位于 `data.page/limit/total` |
| `GET /api/merchant/articles/{id}` | 读取所属文章，包含正文；越权或不存在返回 404 |
| `POST /api/merchant/articles` | `{"title":"公告","content":"正文"}`；自动填充商家归属，返回 `data.id/rows` |
| `PUT /api/merchant/articles/{id}` | `{"title":"新标题"}`；绑定 `UpdatePatch<MerchantArticle>`，未提交字段保持不变，显式 null 继续进入校验 |
| `DELETE /api/merchant/articles/{id}` | 调用 `commandGateway.delete(ARTICLES, id)`；归属进入最终 SQL，越权写入返回 409 |

创建 DTO 转为实体，调用 `commandGateway.create(ARTICLES, article)`；更新调用 `commandGateway.update(ARTICLES, id, patch)`。写入返回 `EntityWriteResult<Long>`。内核执行字段和实体约束校验，客户端不能写入商家归属、未知字段或更换路径中的主键目标。列表返回 `MerchantArticleSummary`，详情返回 `MerchantArticle`，Controller 无需接触 `CrudRecord` 或写入字段 Map。查询审计和写入事务审计由内核记录，不在 Controller 再套一层 `BusinessQueryExecutor`。

## 商品：完整通用 CRUD，声明轻度复杂规则

[MerchantProduct](../src/main/java/com/example/minicommerce/merchant/entity/MerchantProduct.java) 已集中表达以下规则：

| 业务要求 | 声明方式 |
| --- | --- |
| 商家仅维护本店商品 | merchant 授权 + 全局 `merchantId` 归属约定 |
| 匿名与顾客只读启用商品 | view 授权 + `scope = ALL, where = "active = true"` |
| 公开浏览只显示基本信息 | `@EntCrudGrant.Fields` 白名单 `id/merchantId/name/price` |
| 限制返回、筛选、排序和写入字段 | `@EntCrudEntity.fieldLimits` |
| 价格必须为正且最多六位小数 | `@DecimalMin` / `@Digits` |
| 写入时避免并发覆盖 | `version` 默认乐观锁约定 |
| 商家可批量改价 | `addActions = "batch-update"` 与对应 merchant 授权 |

直接调用 `/api/ent-crud/merchantProduct/create/page/detail/update/updateBatch/delete` 即可运行；不需要商品专用 Controller、查询 Service 或 Handler。静态上架条件同时用于列表、详情和计数；商家可以读取本店停用商品，公开分支不能读取它们。批量更新复用字段校验、逐项归属检查、版本检查和整批事务。

商品保留的 MyBatis-Plus Mapper / Service 仅供订单业务流程读取商品，不承载这条通用 CRUD 链路。

## 订单：按动作选择入口，Controller 与 Handler 组合

| 动作 | 唯一 HTTP 入口 | 执行链 |
| --- | --- | --- |
| 下单 | `POST /api/orders` | CommerceOrderController → CommandGateway → PlaceOrderHandler → CommerceOrderCommandService |
| 取消 | `POST /api/ent-crud/commerceOrder/action/cancel` | 通用 Controller → CommandGateway → CancelOrderHandler → CommerceOrderCommandService |
| 查询 | `/api/ent-crud/commerceOrder/page/detail` 等 | 通用查询流程 |

下单采用明确的业务接口，请求为 `{"items":[{"merchantProductId":101,"quantity":2}]}`，响应保留 `data.orderIds/totalAmount`。Controller 使用 `@EntAccess(accessEntries = "commerce")` 建立可信作用域，调用 `CrudResponse.from(commandGateway.actionResult(PLACE_ORDER, input))`，统一转换 HTTP 响应，保留业务结果、状态、消息和重放标记；`PLACE_ORDER` 与 Handler 注解引用的 `CommerceOrderActions.PlaceOrder` 共用实体、动作及输入输出契约。身份、归属和事务仍由既有授权与业务执行链处理，不直接调用 Service。

订单声明 `httpExcludedActions = CommerceOrder.CREATE`，仅关闭通用 `/action/create` HTTP，不移除内部动作或客户下单授权；`operations = "create="` 继续禁止普通 CRUD 创建绕过业务动作。取消接口的标准契约已满足需求，因此客户、商家和管理员共用通用入口，授权限制订单范围，Service 检查状态与并发条件。每个动作仅保留一个对外入口，HTTP 契约与业务动作独立组合。

动作定义集中在 [CommerceOrderActions](../src/main/java/com/example/minicommerce/order/action/CommerceOrderActions.java)，下单与取消 Handler 均通过注解引用对应定义类。业务代码直接取得结果，HTTP 适配保留完整执行状态：

```java
// Java 业务调用：类型由共享动作定义约束。
PlaceOrderResult result = commandGateway.action(PLACE_ORDER, input);

// HTTP 接入：统一保留状态、业务码、消息与重放标记。
return CrudResponse.from(commandGateway.actionResult(PLACE_ORDER, input));
```

简单业务可以直接实现在 Handler 中；需要复用业务能力或组织复杂事务时再委托 Service。订单保留命令服务承载拆单和事务编排，不要求所有业务都增加这一层。

请求见 [commerce.http](../requests/commerce.http)。

## 验证

在示例目录使用 JDK 21：

```bash
../../mvnw -Dtest=MerchantArticleIntegrationTest,MerchantProductUpdateIntegrationTest,PlaceOrderIntegrationTest,CommerceOrderAccessTest test
```

文章测试覆盖自定义分页、详情、增改删、商家隔离、归属填充、实体校验、通用 HTTP 关闭及审计链路；商品测试覆盖公开投影、所属数据、金额校验、字段限制、单条与批量写入、乐观锁和事务回滚。

订单测试覆盖自定义下单入口、身份限制、可信归属、拆单、快照、事务回滚、通用下单入口关闭，以及客户、商家和管理员共用通用取消入口。
