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

`MerchantProduct` 是商家独立维护的可售商品，名称、销售价、启用状态和版本都属于商家：

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
| 双端销售订单分页 | [order-query.http](requests/order-query.http) | 客户按客户范围读取，商家按店铺范围读取 |
| 实体文档 | [documentation.http](requests/documentation.http) | Meta 实体契约和主体授权 |

## 代码导航

代码按 `customer`、`merchant`、`order` 业务分包；跨业务复用的能力放在 `common`。

```text
src/main/java/com/example/minicommerce/
├── MiniCommerceApplication.java
├── common/
│   ├── access/       # 客户只读、商家只读、商家维护策略
│   ├── exception/    # 公共业务异常契约
│   └── web/          # 全局 HTTP 异常处理
├── customer/
│   └── entity/
├── merchant/
│   ├── entity/       # MerchantProduct、MerchantArticle
│   ├── dto/
│   ├── enums/
│   ├── service/
│   └── web/
└── order/
    ├── access/       # OrderReadPolicy：客户、商家分别读取本人订单
    ├── entity/       # Order、MerchantOrder、OrderItem
    ├── dao/          # OrderItemDao：自定义明细查询
    ├── dto/          # 下单命令与双端独立分页查询
    ├── enums/
    ├── handler/      # PlaceOrderHandler：下单与按商家拆单
    ├── service/      # MerchantOrderQueryService：销售订单查询
    └── web/          # CustomerOrderController、MerchantOrderController
```

基础 CRUD 直接构造器注入 `EntityDao<实体, 主键>`，无需空 DAO 接口；只有自定义查询的 `OrderItemDao` 声明 `@EntDao`。两种方式均按次解析可信访问范围。

- 下单：[PlaceOrderHandler](src/main/java/com/example/minicommerce/order/handler/PlaceOrderHandler.java)，入口为 `POST /api/ent-crud/order/action/place`。
- 销售订单查询：[MerchantOrderQueryService](src/main/java/com/example/minicommerce/order/service/MerchantOrderQueryService.java)，客户与商家共用授权查询执行器。
- 订单详情：`POST /api/ent-crud/order/detail`，可展开 `customer`、`orderItemList` 和 `merchantOrderList`。
- 访问控制与错误响应：[详细说明](docs/access-control.md)。

测试按 `common`、`merchant`、`order` 分包，分别覆盖公共契约、商家治理和订单业务；订单 DAO 与下单流程独立验证。

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

商家只能读取和修改自己的商品价格。`merchantId` 由认证身份确定，不能通过 Patch 或请求头切换；通用 `merchantProduct` HTTP 默认开放，并复用商家策略；没有商家身份的通用写入请求返回 403。

## 订单范围

`MerchantOrder` 是客户和商家共同读取的销售订单实体：

| 入口 | 固定范围 | 可选筛选 |
| --- | --- | --- |
| `POST /api/consumer/orders/page` | `customerId=当前客户` | `merchantId` |
| `POST /api/merchant/orders/page` | `merchantId=当前商家` | `customerId` |

执行演示销售订单脚本：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/order-query-demo.sql
```

`MerchantOrder.customerId` 和 `MerchantOrder.merchantId` 都是归属字段。框架把身份范围合并到查询和 count SQL，客户不能读取其他客户的订单，商家不能读取其他商家的订单。

## 配置要点

配置分为两个文件，启动时自动加载，无需切换 Profile：

- [application.yml](src/main/resources/application.yml)：运行基础、实体元数据、业务策略、操作与字段授权。
- [demo.yml](src/main/resources/demo.yml)：静态账号、服务端入口路由、通用 CRUD 演示权限和审计设置。

身份归属、策略接口与 YAML 模板、字段权限及统一错误处理见 [访问控制说明](docs/access-control.md)。

## 演示账号

- 客户：`demo-customer-a` → 客户 `301`，`demo-customer-b` → 客户 `302`
- 商家：`demo-merchant-a` → 商家 `1001`，`demo-merchant-b` → 商家 `2001`

正式应用应替换 Token 解析、身份绑定、权限、库存、支付和发货动作。

## 验证

在本目录使用 JDK 21：

```bash
../../mvnw clean test
```

集成测试覆盖商家商品隔离、商家批量改价、客户下单、跨商家拆单、销售订单范围、价格和名称快照、事务回滚以及未暴露实体的拒绝。使用 MySQL 时按上面的初始化 SQL 和请求文件执行同样的流程。
