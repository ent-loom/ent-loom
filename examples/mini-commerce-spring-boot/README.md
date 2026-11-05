# Mini Commerce Spring Boot 示例

这是一个面向多商家的实体编程示例：每个商家独立维护自己的商品和销售订单，客户一次结算可以包含多个商家的商品，系统按商家拆分销售订单。下单时保存商品名称、成交单价、商家和金额快照。

## 启动

开发与测试使用 JDK 21；运行环境需要 MySQL 8+ 和 Docker Compose v2.20+。

CRUD Starter 默认引入 Spring Boot JDBC Starter 和 HikariCP 连接池；本示例声明 MySQL 驱动，并通过 `spring.datasource` 配置连接信息。

```bash
cp .env.example .env
docker compose up -d --wait mysql
../../mvnw spring-boot:run
```

Windows 使用 `Copy-Item .env.example .env` 和 `../../mvnw.cmd spring-boot:run`。默认应用端口为 8082，Compose 数据库端口为 3308。当前开发配置使用 `CREATE_MODIFY_DELETE_ALL`，按实体注解创建缺失表并同步已有表结构，包含删除多余列、重建索引及修改主键；旧列数据会丢失。需要保留旧结构时改用受控修改模式 `CREATE_MODIFY_TABLE_AND_METAS`；生产结构变更使用版本化迁移。

使用当前工作区构件时，先在 `ent-loom` 仓库根目录执行：

```bash
./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter,:ent-loom-ddl-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install
```

## 业务模型

`MerchantProduct` 是商家独立维护的可售商品，名称、销售价、启用状态和版本都属于商家：

| 实体 | 职责 |
| --- | --- |
| `MerchantProduct` | 商家自己的商品，按 `merchantId` 隔离 |
| `Order` | 客户与商家共用的订单，包含 `customerId`、`merchantId`、状态和金额 |
| `OrderItem` | 订单明细，通过 `orderId` 关联订单，保存 `merchantProductId`、名称和成交价快照 |

客户下单提交 `merchantProductId` 和数量。服务端从商家商品记录解析 `merchantId`，不信任请求中的商家归属；同一商家生成一笔 `Order`，不同商家分别生成独立订单。下单结果返回 `orderIds` 和本次合计金额 `totalAmount`；订单及明细在同一事务内保存，任一写入失败则全部回滚。

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
│   ├── exception/    # 公共业务异常契约
│   └── web/          # 全局 HTTP 异常处理
├── customer/
│   └── entity/
├── merchant/
│   └── entity/       # MerchantProduct、MerchantArticle；维护直接复用通用 CRUD
└── order/
    ├── entity/       # Order、OrderItem
    ├── dao/          # OrderItemDao：自定义明细查询
    ├── dto/          # 下单命令与结果
    ├── enums/
    └── handler/      # PlaceOrderHandler：下单与按商家拆单
```

基础 CRUD 直接构造器注入 `EntityDao<实体, 主键>`，无需空 DAO 接口；只有自定义查询的 `OrderItemDao` 声明 `@EntDao`。两种方式均按次解析可信访问范围。

- 下单：[PlaceOrderHandler](src/main/java/com/example/minicommerce/order/handler/PlaceOrderHandler.java)，入口为 `POST /api/ent-crud/order/action/place`。
- 销售订单查询：`POST /api/ent-crud/order/page`，客户与商家共用通用 CRUD，身份决定归属范围。
- 订单详情：`POST /api/ent-crud/order/detail`，可展开 `customer` 和 `orderItemList`。
- 访问控制与错误响应：[详细说明](docs/access-control.md)。

测试按 `common`、`merchant`、`order` 分包，分别覆盖公共契约、商家治理和订单业务；订单 DAO 与下单流程独立验证。

## 商家商品治理

启动应用后执行初始化数据：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/merchant-demo.sql
```

商家商品接口：

```bash
curl -X POST http://localhost:8082/api/ent-crud/merchantProduct/list \
  -H 'Authorization: Bearer demo-merchant-a' \
  -H 'Content-Type: application/json' \
  -d '{"options":{"limit":100}}'
curl -X POST http://localhost:8082/api/ent-crud/merchantProduct/updateBatch \
  -H 'Authorization: Bearer demo-merchant-a' \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"items":[{"id":101,"price":11.50,"version":0}]}}'
```

商家只能读取和维护自己的商品，写入字段限定为 `name/price/active`。列表与批量更新均复用通用 CRUD，无需商品专用 Controller、Service 或 DTO。批量更新返回 `data.rows` 和按输入顺序排列的 `data.items`；价格约束声明在实体，任一项失败整批回滚，写入未命中统一返回 `409 WRITE_CONFLICT`。`merchantId` 由认证身份确定，不能通过 Patch 或请求头切换；通用 `merchantProduct` HTTP 默认开放，并复用商家策略；没有商家身份的通用写入请求返回 403。

## 标准查询无需业务 Controller

已绑定访问策略的实体，通用 `list/page/findOne/detail` 会在框架入口完成可信身份准入，再统一执行操作授权、归属范围、字段投影和审计。默认要求有效 Token；实体已暴露不代表已获授权。

| 实体 | 通用查询入口 | 数据范围 |
| --- | --- | --- |
| 商家商品 | `/api/ent-crud/merchantProduct/list`、`page`、`findOne`、`detail` | 当前商家 |
| 客户地址 | `/api/ent-crud/customerAddress/list`、`page`、`findOne`、`detail` | 当前客户 |
| 商家文章 | `/api/ent-crud/merchantArticle/list`、`page`、`findOne`、`detail` | 当前商家 |
| 订单 | `/api/ent-crud/order/list`、`page`、`findOne`、`detail` | 当前客户或商家 |

请求条件放在 `options`，例如 `{"options":{"page":1,"limit":20,"filter":{"active":true}}}`。分页返回 `data.items` 和 `data.page`；显式筛选与授权范围取交集，不接受客户端指定当前身份。

订单详情可用 `expandRelations: [customer, orderItemList]`，返回受身份字段方案约束的关联记录；客户可看本人邮箱，商家只看客户 ID 和名称。每个关系最多加载 1000 条记录，超限明确拒绝。实体/DTO 输出、查询场景、多实体连接和嵌套展开仍拒绝。详见[受授权关联读取](../../docs/guides/crud/受授权关联读取.md)。

## 订单范围

`Order` 是客户和商家共同读取的订单实体：

| 当前可信身份 | 共用入口 | 固定范围 | 可选筛选 |
| --- | --- | --- | --- |
| 客户 | `POST /api/ent-crud/order/page` | `customerId=当前客户` | `merchantId` |
| 商家 | `POST /api/ent-crud/order/page` | `merchantId=当前商家` | `customerId` |

执行演示销售订单脚本：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/order-query-demo.sql
```

`Order` 在 `ent/entities/order.yml` 中通过 `crud.policy: order-read` 选择全局策略。框架把身份范围合并到数据与 count SQL，客户端额外筛选只能收窄；不能通过请求体或 Header 指定当前客户、商家。字段差异在实体 YAML 的 `crud.fields` 中声明，`RETURN` 默认开放普通字段，`FILTER/EXPAND` 按身份配置；`SORT` 缺省复用可返回且元数据允许排序的普通字段；只有身份差异才需要显式配置 SORT。分页缺省每页 10 条，默认按创建时间、主键倒序；创建时间从全局命名约定生成的元数据角色读取，无需实体逐字段注解，返回 `data.items/data.page`。

订单读取不再使用 `local-developer` 演示权限，缺少有效 Token 返回 403。下单 `place` 仍经过显式动作的入口、能力和主体场景授权，保留跨商家拆单与事务；它尚未迁入可信客户创建授权，当前 `customerId` 是演示结算对象。正式下单应改为从可信客户身份确定归属。普通订单创建、修改和删除继续拒绝。

## 配置要点

配置分为两个文件，启动时自动加载，无需切换 Profile：

- [application.yml](src/main/resources/application.yml)：运行基础、实体元数据、共享访问策略和实体文档配置。
- [demo.yml](src/main/resources/demo.yml)：静态账号、服务端入口路由、通用 CRUD 演示权限和审计设置。

身份归属、策略模板、字段权限及统一错误处理见 [访问控制说明](docs/access-control.md)。

## 演示账号

- 客户：`demo-customer-a` → 客户 `301`，`demo-customer-b` → 客户 `302`
- 商家：`demo-merchant-a` → 商家 `1001`，`demo-merchant-b` → 商家 `2001`

正式应用应替换 Token 解析、身份绑定、权限、库存、支付和发货动作。

## 验证

在本目录使用 JDK 21：

```bash
../../mvnw clean test
```

集成测试默认使用 H2，覆盖商家商品隔离、商家批量改价、客户下单、跨商家拆单、销售订单范围、价格和名称快照、事务回滚以及未暴露实体的拒绝。

真实 MySQL 8 验收复用下单与双端分页用例，并额外检查自动建表、金额类型和索引；执行方法见 [MySQL 8 验收](docs/mysql-acceptance.md)。验收使用独立空库，会清空其中的客户、商家商品、订单和明细数据。

## 实体声明

`src/main/resources/ent/entities` 集中每个实体的策略、字段权限、动作、输入契约与索引。Order 声明下单动作和客户/商家索引，MerchantProduct 显式开放批量更新，Customer 通过 access: JAVA 复用现有治理链。Java 保留字段、关系结构、基础说明和 Handler。操作逐动作覆盖，空列表保留业务动作而关闭通用映射；批量逐目标授权，任一失败整批回滚。
