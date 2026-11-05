# 访问控制与错误响应

本示例使用实体访问策略表达客户与商家的归属范围。启动和演示入口见 [README](../README.md)。

## 配置职责

- [application.yml](../src/main/resources/application.yml)：身份、关系、共享策略模板和实体文档配置。
- [demo.yml](../src/main/resources/demo.yml)：演示账号、服务端入口路由、主体权限和审计设置。

## 身份与归属

主配置使用以下关系约定，按实体已有字段缺省推导归属保护（位于 `ent.loom.crud` 下）：

```yaml
access:
  relations:
    merchant:
      field: merchantId
    customer:
      field: customerId
controller:
  enabled: true
```

开启 `controller.enabled` 后，HTTP 实体必须有 `ent/entities/<资源码>.yml`，并显式选择 policy 或 access: JAVA。Order 通过文件绑定 order-read，orderItem 通过 crud.http-exposed: false 关闭通用 HTTP。动作、字段授权、归属与操作映射均在实体文件；开放映射不授予权限。下单 Handler 仍使用 Java 注解注册与 EntityDao 执行业务。归属字段缺省受保护，ownership: [] 可关闭推导，底层范围约束仍保留。

身份统一定义在 `ent.loom.crud.access.identities`，示例无需客户/商家身份类：

```yaml
identities:
  customer:
    id-type: LONG
  merchant:
    id-type: LONG
```

身份 ID 字段默认采用“身份码 + Id”，因此无需重复声明 `customerId` / `merchantId`。项目按业务规则定义策略模板，实体文件通过 crud.policy 选择策略，crud.operations/relations/ownership 声明差异；范围默认使用身份同名关系。`RETURN` 缺省为全部普通字段，`FILTER/SORT` 缺省分别复用其中可筛选/可排序的字段，写入继续显式约束。普通排序无需逐实体重复声明 id、createdAt；运行时元数据的 sortable 静态上限始终生效，身份差异使用显式 SORT 模式整体覆盖，NONE 关闭调用方排序。

实体文件通过 `crud.policy` 显式选择全局共享策略，CRUD 启动期编译到既有授权模型；文件与 Java Bean 重复绑定拒绝。实体字段差异由 `crud.fields` 声明，Java 保留类型、基础描述、关系结构及 Handler。

| 策略模板 | 业务规则 | 实体声明 |
| --- | --- | --- |
| `customer-read` | 客户只读本人记录 | [customerAddress.yml](../src/main/resources/ent/entities/customerAddress.yml) |
| `merchant-read` | 商家只读本人记录 | [merchantArticle.yml](../src/main/resources/ent/entities/merchantArticle.yml) |
| `merchant-manage` | 商家读取、维护本人数据 | [merchantProduct.yml](../src/main/resources/ent/entities/merchantProduct.yml) |
| `order-read` | 客户、商家分别读取自己的订单 | [order.yml](../src/main/resources/ent/entities/order.yml) |

订单标准读取统一经过框架全局注册表、Gateway 和受授权执行器，不再由示例手动组装授权服务。`view` 映射 `LIST/PAGE/FIND_ONE/DETAIL`；客户限定 `customerId`，商家限定 `merchantId`，数据和 count SQL 使用同一范围。请求筛选只能收窄；客户的 `FILTER` 不授予 `customerId`，商家不授予 `merchantId`。

订单字段差异在 `ent/entities/order.yml` 的 `crud.fields` 声明。客户 FILTER 排除 customerId/totalAmount，商家 FILTER 排除 merchantId/totalAmount；客户 EXPAND 使用 ALL，商家仅排除 customer.email。新增合格字段自动开放，需要固定集合使用 INCLUDE。

框架先读取根订单，再按实际连接键批量补数；内部连接字段不自动公开，关联逻辑删除仍生效。明细独立 HTTP 保持关闭；每个关系单次最多 1000 条，超限明确失败。实体/DTO 输出、查询场景、多实体和嵌套展开仍拒绝。排除关联子字段时先编译为字段路径，不保留可绕过排除的整段关系授权。

没有字段差异时无需声明注解；RETURN/FILTER/SORT 沿用元数据缺省，EXPAND/WRITE/STATS 省略则不授权。完整缺省表见[配置参考](../../../docs/guides/配置参考.md#entloomcrudaccess)。

MerchantProduct 在实体文件的 merchant.WRITE 使用 ALL，允许 name/price/active 等元数据可写字段；主键、版本、归属、只读和不可变字段始终受保护。批量 update 显式映射 UPDATE_BATCH，每项目标独立授权。

自定义 Controller 使用 `@EntAccess(identity = "customer")` 或 `"merchant"`，方法接收统一 `AccessContext`。框架按身份码检查准入并构造不可变身份快照，数据范围从已声明 ID 属性读取；启动时检查未知引用及身份/实体字段类型不匹配。

已绑定策略实体的通用 `list/page/findOne/detail` 和标准命令 无需业务 Controller 声明 `@EntAccess`：框架按实际框架 Handler 和路由实体解析 HTTP 暴露与策略绑定，使用 REQUIRED 准入模式创建当前访问者，随后由既有 Gateway、授权执行器校验操作、归属和字段用途。认证来源仍为 `CrudHttpAuthenticationSource`，缺失来源、缺失或无效凭证均拒绝；客户端请求体和身份 Header 不参与准入。未绑定策略实体保持原治理链。新授权查询及命令不再调用旧 `CrudSubjectResolver`，默认主体与旧 grants 不授予它任何权限。

商品列表已迁移为 `POST /api/ent-crud/merchantProduct/list`，返回 `data.items`；商品分页、客户地址和商家文章也可直接使用通用查询入口，数据与 count SQL 同时受身份范围约束。商品维护统一使用通用 CRUD，批量改价使用 `POST /api/ent-crud/merchantProduct/updateBatch`，请求为 `payload.items[]`。商品无需专用 Controller、Service 或 DTO；价格约束来自实体字段上的校验注解，逐目标授权、版本和整批事务由框架执行。通用入口支持根实体 `CrudRecord` 及显式授权的本地一级关联投影，详见[受授权关联读取](../../../docs/guides/crud/受授权关联读取.md)。

演示账号集中配置在 `demo.yml` 的 `ent.loom.crud.example.accounts`，每项只声明 `token`、`subject-id` 和已定义的 `customerId` 或 `merchantId`，归属由服务端账号绑定；入口独立由 `example.routes` 和 `default-access-entry` 确定。正式接入使用可信认证来源与身份解析器，YAML 定义不代替归属核验。

未绑定策略的客户基础 CRUD 仍复用演示主体 `local-developer`。订单读取已经迁入全局策略，默认主体和旧 grants 不再授予订单读取权限。

`place` 是实体显式声明的业务动作，未提供新策略 ACTION 映射时继续通过原有场景治理：校验入口、能力、主体权限，再执行 Handler、事务与审计；不是授权失败后的旧链回退。若项目显式提供新策略 ACTION 映射，该映射优先，未适配的执行形状明确拒绝。可信作用域禁止降级，普通写入和未知动作也不会因实体上存在 Handler 获准执行。

当前下单演示仍由 `local-developer` 的 `order:place` 授权，`customerId` 表示演示结算对象；它尚未迁入可信客户的聚合创建授权。正式下单需从可信客户身份确定归属，并显式授权商品读取与聚合创建。`governance.access-entry: consumer` 与 `place` 声明一致；标准订单读取在演示认证来源中使用公共 `commerce` 入口，当前身份只由服务端账号绑定确定。

## 统一错误响应

自定义接口的通用异常集中由 [ApiExceptionHandler](../src/main/java/com/example/minicommerce/common/web/ApiExceptionHandler.java) 处理：框架异常复用 `CrudHttpErrorMapper`，参数校验返回 DTO 声明的中文提示，请求体解析失败返回统一的 400 提示；Spring HTTP 异常保留原状态和响应头，未知异常记录日志并返回通用 500 提示。

应用业务失败统一抛出 [BusinessException](../src/main/java/com/example/minicommerce/common/exception/BusinessException.java)，继承 `EntBusinessException` 并复用框架全局处理器。各模块错误枚举实现 `BusinessError`，声明错误码、中文提示和 HTTP 状态，默认 400；例如 `new BusinessException(OrderError.ITEMS_REQUIRED)`。通用写入的不存在、跨范围或版本冲突由框架统一返回 `409 WRITE_CONFLICT`，不区分具体未命中原因，无需业务转换异常。

该 Advice 限定应用包；尚未匹配到 Controller 的路由错误由 Spring 默认处理，例如方法不支持时保留 405 和 `Allow` 响应头。

创建时间命名由全局约定识别，默认支持 `createTime`、`createdAt`，无需逐实体或逐字段添加时间角色注解。项目命名不同时，在 `ent.loom.meta.defaults.created-time-fields` 统一配置，列表整体替换默认名称；共享实体基类同样生效。SORT 权限按可返回且可排序字段推导，不依赖这些具体名称。普通查询和受授权查询共用默认排序解析器，按唯一 CREATED_TIME 字段及实际主键排序，方向由 `crud.query.default-sort` 统一配置，默认均为倒序。
