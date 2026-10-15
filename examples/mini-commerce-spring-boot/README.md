# Mini Commerce Spring Boot 示例

这是一个面向多商家的实体编程示例：每个商家独立维护自己的商品和销售订单，客户一次结算可以包含多个商家的商品，系统按商家拆分销售订单。下单时保存商品名称、成交单价、商家和金额快照。

## 启动

开发与测试使用 JDK 21；运行环境需要 MySQL 8+ 和 Docker Compose v2.20+。

```bash
cp .env.example .env
docker compose up -d --wait mysql
../../mvnw spring-boot:run
```

Windows 使用 `Copy-Item .env.example .env` 和 `../../mvnw.cmd spring-boot:run`。默认应用端口为 8082，Compose 数据库端口为 3308。应用按实体注解自动创建缺失表，不清空已有数据；生产结构变更使用版本化迁移。

使用当前工作区构件时，先在 `ent-loom` 仓库根目录执行：

```bash
./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter,:ent-loom-ddl-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install
```

## 业务模型

示例不再维护平台全局 `Product`。`MerchantProduct` 是真正可售的商品，名称、销售价、启用状态和版本都属于商家：

| 实体 | 职责 |
| --- | --- |
| `MerchantProduct` | 商家自己的商品，按 `merchantId` 隔离 |
| `Order` | 客户一次结算的聚合记录，可跨商家 |
| `MerchantOrder` | 商家的独立销售订单，包含 `merchantId`、客户、状态和金额 |
| `OrderItem` | 订单明细，保存 `merchantProductId`、`merchantId`、名称和成交价快照 |

客户下单提交 `merchantProductId` 和数量。服务端从商家商品记录解析 `merchantId`，不信任请求中的商家归属；同一商家生成一个 `MerchantOrder`，不同商家分别生成独立销售订单。

如果将来需要跨商家共用标准商品目录，可以另外增加不参与成交的 `CatalogProduct`/SPU 层；本示例当前不引入该层。

## 演示场景

| 场景 | 请求文件 | 内容 |
| --- | --- | --- |
| 商城业务闭环 | [commerce.http](requests/commerce.http) | 客户、商家商品、跨商家结算、销售订单和成交快照 |
| 通用 CRUD | [crud.http](requests/crud.http) | 客户创建、更新、分页、详情和删除拒绝 |
| 商家商品治理 | [merchant.http](requests/merchant.http) | 商家商品读取隔离、批量改价、乐观锁和整批回滚 |
| 双端销售订单分页 | [order-entries.http](requests/order-entries.http) | 客户按客户范围读取，商家按店铺范围读取 |
| 实体文档 | [documentation.http](requests/documentation.http) | Meta 实体契约和主体授权 |

代码按 `customer`、`merchant`、`order` 业务分包。下单入口仍是 `POST /api/ent-crud/order/action/place`，订单详情是 `POST /api/ent-crud/order/detail`；详情可展开 `customer`、`orderItemList` 和 `merchantOrderList`。

## 商家商品治理

启动应用后执行初始化数据：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/merchant-demo.sql
```

商家商品接口：

```bash
curl -H 'Authorization: Bearer demo-merchant-a' http://localhost:8082/api/merchant/products
curl -X POST http://localhost:8082/api/merchant/products/prices \
  -H 'Authorization: Bearer demo-merchant-a' \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"id":101,"price":11.50,"version":0}]}'
```

商家只能读取和修改自己的商品价格。`merchantId` 由认证身份确定，不能通过 Patch 或请求头切换；通用 `merchantProduct` CRUD 不开放。

## 订单范围

`MerchantOrder` 是客户和商家共同读取的销售订单实体：

| 入口 | 固定范围 | 可选筛选 |
| --- | --- | --- |
| `POST /api/consumer/orders/page` | `customerId=当前客户` | `merchantId` |
| `POST /api/merchant/orders/page` | `merchantId=当前商家` | `customerId` |

执行演示销售订单脚本：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/order-entry-demo.sql
```

`MerchantOrder.customerId` 和 `MerchantOrder.merchantId` 都是归属字段。框架把身份范围合并到查询和 count SQL，客户不能读取其他客户的订单，商家不能读取其他商家的订单。

## 配置要点

配置分为两个文件，启动时自动加载，无需切换 Profile：

- [application.yml](src/main/resources/application.yml)：运行基础、实体元数据、业务策略、操作与字段授权。
- [demo.yml](src/main/resources/demo.yml)：静态账号、服务端入口路由、通用 CRUD 演示权限和审计设置。

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

开启 `controller.enabled` 后，已注册实体默认开放通用 CRUD HTTP，无需声明 `@EntCrudEntity` 或登记 YAML 清单；开放不授予操作权限。`merchantProduct` 默认开放并复用商家策略，专用接口仍可使用；`merchantOrder` 仅供双端定制分页、`orderItem` 仅供内部持久化，两者通过 `@EntCrudEntity(httpExposed = false)` 关闭通用 HTTP。`access.entities` 仅用于差异配置，支持 `http-exposed: false`；`access.entity-options` 只声明业务访问差异，不改变 HTTP 暴露。`Order` 仅允许 `place` 场景，普通创建、修改和删除默认拒绝。`Order.customerId` 及 `MerchantOrder` 的 customerId/merchantId 默认获得归属保护；下单 Handler 使用 EntityDao 在创建时赋值，普通更新不能改归属。实体省略 `ownership` 时按实际存在的关系字段推导；显式列表整体替换推导结果，`ownership: []` 关闭推导。归属声明不授予权限，也不自动添加查询范围。

身份统一定义在 `ent.loom.crud.access.identities`，示例无需客户/商家身份类：

```yaml
identities:
  customer:
    id-type: LONG
  merchant:
    id-type: LONG
```

身份 ID 字段默认采用“身份码 + Id”，因此无需重复声明 `customerId` / `merchantId`。项目按业务规则定义策略模板，实体通过业务分类接口选择；`entities` 声明 HTTP 与业务访问差异，`entity-options` 仅声明业务访问差异；范围默认使用身份同名关系。`RETURN` 缺省为全部普通字段，`FILTER` 缺省复用其中可筛选的字段，写入继续显式约束。

业务分类接口统一放在项目级 `com.example.minicommerce.access` 包，供各业务模块复用。接口通过 `@EntCrudPolicy` 集中选择策略，并声明必要的归属 getter；实体只增加 `implements`，方法由 Lombok 生成，无需在 YAML 的 `policy-bindings` 再次登记。框架从接口读取策略，复用同一编译链推导标准操作、归属范围及字段授权；实现接口不改变通用 HTTP 暴露，也不授予全部字段写入权限。同一实体不能同时通过多个分类接口或接口与 YAML 重复选择策略。

| 策略模板 | 分类接口 | 业务规则 | 当前实体 |
| --- | --- | --- | --- |
| `customer-record` | [CustomerRecord](src/main/java/com/example/minicommerce/access/CustomerRecord.java) | 客户只读本人记录 | 客户地址 |
| `merchant-record` | [MerchantRecord](src/main/java/com/example/minicommerce/access/MerchantRecord.java) | 商家只读本人记录 | 商家文章 |
| `merchant-data` | [MerchantData](src/main/java/com/example/minicommerce/access/MerchantData.java) | 商家读取、维护本人数据 | 商家商品 |
| `order-entries` | [OrderEntries](src/main/java/com/example/minicommerce/access/OrderEntries.java) | 客户、商家分别读取自己的订单 | 商家销售订单 |

字段权限按“实体 → 身份 → 用途”集中声明：`field-plans.merchantProduct.merchant.WRITE` 使用 `mode: INCLUDE` 和 `fields: [name, price, active]`，只授予商家这些字段的写入权限，不表示必填或必须全部修改。`EXCLUDE` 从元数据允许的字段中排除指定字段，`ALL/NONE` 明确表达全部允许或全部关闭；省略 WRITE 不授权，主键、版本、归属、只读及不可变字段始终受保护。同类新实体实现对应分类接口，标准动作自动推导操作目录，只补必要字段约束或特殊 CRUD 映射；`view` 缺省映射 LIST/PAGE/FIND_ONE/DETAIL，`update` 映射 UPDATE，批量与业务动作仍显式声明。特殊业务另选策略。模板由项目声明，框架不内置业务身份或自动合并分类权限。

Controller 使用 `@EntAccess(identity = "customer")` 或 `"merchant"`，方法接收统一 `AccessContext`。框架按身份码检查准入并构造不可变身份快照，数据范围从已声明 ID 属性读取；启动时检查未知引用及身份/实体字段类型不匹配。

演示账号集中配置在 `demo.yml` 的 `ent.loom.crud.example.accounts`，每项只声明 `token`、`subject-id` 和已定义的 `customerId` 或 `merchantId`，归属由服务端账号绑定；入口独立由 `example.routes` 和 `default-access-entry` 确定。正式接入使用可信认证来源与身份解析器，YAML 定义不代替归属核验。

通用 CRUD 复用演示模式的默认主体 `local-developer` 和默认不限制数据归属的基础范围解析器；声明式策略仍约束双端接口的身份范围与字段。`governance.grants` 按主体匹配，入口名称本身不授予权限；创建必填字段保留在主配置中，通用 HTTP 特殊关闭声明放在实体注解上。

当前账号：

- 客户：`demo-customer-a` → 客户 `301`，`demo-customer-b` → 客户 `302`
- 商家：`demo-merchant-a` → 商家 `1001`，`demo-merchant-b` → 商家 `2001`

正式应用应替换 Token 解析、身份绑定、权限、库存、支付和发货动作。

## 验证

在本目录使用 JDK 21：

```bash
../../mvnw clean test
```

集成测试覆盖商家商品隔离、商家批量改价、客户下单、跨商家拆单、销售订单范围、价格和名称快照、事务回滚以及未暴露实体的拒绝。使用 MySQL 时按上面的初始化 SQL 和请求文件执行同样的流程。
