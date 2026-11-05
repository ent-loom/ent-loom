# 两种 CRUD 接入演示

两个实体分别展示不同接入方式，均使用同一 CRUD 内核处理授权与持久化。Java 业务默认使用实体，固定投影使用 DTO；通用 HTTP 的动态字段结果保留动态记录。类型合同见[实体优先的类型化 CRUD](../../../docs/guides/crud/实体优先的类型化CRUD.md)。

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
    .page(new PageRequest(query.page(), query.limit())).build();
return CrudResponse.success(queryGateway.page(spec));
```

网关从可信作用域取得访问者，内核执行授权、商家范围与分页计数。Controller 无需注入访问者、自行解析 `AuthorizationScope` 或为计数重复编写范围 SQL。

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

## 验证

在示例目录使用 JDK 21：

```bash
../../mvnw -Dtest=MerchantArticleIntegrationTest,MerchantProductUpdateIntegrationTest test
```

文章测试覆盖自定义分页、详情、增改删、商家隔离、归属填充、实体校验、通用 HTTP 关闭及审计链路；商品测试覆盖公开投影、所属数据、金额校验、字段限制、单条与批量写入、乐观锁和事务回滚。
