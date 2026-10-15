# 访问控制与错误响应

本示例使用实体访问策略表达客户与商家的归属范围。启动和演示入口见 [README](../README.md)。

## 配置职责

- [application.yml](../src/main/resources/application.yml)：身份、关系、策略模板、字段授权和实体文档配置。
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

开启 `controller.enabled` 后，已注册实体默认开放通用 CRUD HTTP，无需声明 `@EntCrudEntity` 或登记 YAML 清单；开放不授予操作权限。`merchantProduct` 默认开放并复用商家策略，专用接口仍可使用；`merchantOrder` 仅供双端定制分页、`orderItem` 仅供内部持久化，两者通过 `@EntCrudEntity(httpExposed = false)` 关闭通用 HTTP。`access.entity-options` 仅声明操作、关系与归属差异；HTTP 暴露统一由实体元数据控制。`Order` 仅允许 `place` 场景，普通创建、修改和删除默认拒绝。`Order.customerId` 及 `MerchantOrder` 的 customerId/merchantId 默认获得归属保护；下单 Handler 使用 EntityDao 在创建时赋值，普通更新不能改归属。实体省略 `ownership` 时按实际存在的关系字段推导；显式列表整体替换推导结果，`ownership: []` 关闭推导。归属声明不授予权限，也不自动添加查询范围。

身份统一定义在 `ent.loom.crud.access.identities`，示例无需客户/商家身份类：

```yaml
identities:
  customer:
    id-type: LONG
  merchant:
    id-type: LONG
```

身份 ID 字段默认采用“身份码 + Id”，因此无需重复声明 `customerId` / `merchantId`。项目按业务规则定义策略模板，实体通过访问策略接口选择；`entity-options` 仅声明操作、关系与归属差异；范围默认使用身份同名关系。`RETURN` 缺省为全部普通字段，`FILTER` 缺省复用其中可筛选的字段，写入继续显式约束。

跨业务复用的访问策略接口放在 `common/access`；订单专属的双端读取策略放在 `order/access`，与订单业务一起维护。接口通过 `@EntCrudPolicy` 集中选择策略，并声明必要的归属 getter；实体只增加 `implements`，方法由 Lombok 生成，策略绑定统一以注解为入口。框架从接口读取策略，复用同一编译链推导标准操作、归属范围及字段授权；实现接口不改变通用 HTTP 暴露，也不授予全部字段写入权限。同一实体不能同时通过多个策略接口或接口与 Java Bean 重复选择策略。

| 策略模板 | 策略接口 | 业务规则 | 当前实体 |
| --- | --- | --- | --- |
| `customer-read` | [CustomerReadPolicy](../src/main/java/com/example/minicommerce/common/access/CustomerReadPolicy.java) | 客户只读本人记录 | 客户地址 |
| `merchant-read` | [MerchantReadPolicy](../src/main/java/com/example/minicommerce/common/access/MerchantReadPolicy.java) | 商家只读本人记录 | 商家文章 |
| `merchant-manage` | [MerchantManagePolicy](../src/main/java/com/example/minicommerce/common/access/MerchantManagePolicy.java) | 商家读取、维护本人数据 | 商家商品 |
| `order-read` | [OrderReadPolicy](../src/main/java/com/example/minicommerce/order/access/OrderReadPolicy.java) | 客户、商家分别读取自己的订单 | 商家销售订单 |

字段权限按“实体 → 身份 → 用途”集中声明：`field-plans.merchantProduct.merchant.WRITE` 使用 `mode: INCLUDE` 和 `fields: [name, price, active]`，只授予商家这些字段的写入权限，不表示必填或必须全部修改。`EXCLUDE` 从元数据允许的字段中排除指定字段，`ALL/NONE` 明确表达全部允许或全部关闭；省略 WRITE 不授权，主键、版本、归属、只读及不可变字段始终受保护。同类新实体实现对应策略接口，标准动作自动推导操作目录，只补必要字段约束或特殊 CRUD 映射；`view` 缺省映射 LIST/PAGE/FIND_ONE/DETAIL，`update` 映射 UPDATE，批量与业务动作仍显式声明。特殊业务另选策略。模板由项目声明，框架不内置业务身份或自动合并分类权限。

Controller 使用 `@EntAccess(identity = "customer")` 或 `"merchant"`，方法接收统一 `AccessContext`。框架按身份码检查准入并构造不可变身份快照，数据范围从已声明 ID 属性读取；启动时检查未知引用及身份/实体字段类型不匹配。

演示账号集中配置在 `demo.yml` 的 `ent.loom.crud.example.accounts`，每项只声明 `token`、`subject-id` 和已定义的 `customerId` 或 `merchantId`，归属由服务端账号绑定；入口独立由 `example.routes` 和 `default-access-entry` 确定。正式接入使用可信认证来源与身份解析器，YAML 定义不代替归属核验。

通用 CRUD 复用演示模式的默认主体 `local-developer` 和默认不限制数据归属的基础范围解析器；声明式策略仍约束双端接口的身份范围与字段。`governance.grants` 按主体匹配，入口名称本身不授予权限；创建必填字段保留在主配置中，通用 HTTP 特殊关闭声明放在实体注解上。

## 统一错误响应

自定义接口的通用异常集中由 [ApiExceptionHandler](../src/main/java/com/example/minicommerce/common/web/ApiExceptionHandler.java) 处理：框架异常复用 `CrudHttpErrorMapper`，参数校验返回 DTO 声明的中文提示，请求体解析失败返回统一的 400 提示；Spring HTTP 异常保留原状态和响应头，未知异常记录日志并返回通用 500 提示。

应用业务失败统一抛出 [BusinessException](../src/main/java/com/example/minicommerce/common/exception/BusinessException.java)，继承 `EntBusinessException` 并复用框架全局处理器。各模块错误枚举实现 `BusinessError`，声明错误码、中文提示和 HTTP 状态，默认 400；例如 `new BusinessException(OrderError.ITEMS_REQUIRED)`。改价 Service 将写入未命中转换为 `new BusinessException(MerchantError.PRICE_UPDATE_REJECTED, cause)`，保留原始异常，统一返回 409；Controller 无需声明异常处理方法。

该 Advice 限定应用包；尚未匹配到 Controller 的路由错误由 Spring 默认处理，例如方法不支持时保留 405 和 `Allow` 响应头。
