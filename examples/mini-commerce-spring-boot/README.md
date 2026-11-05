# Mini Commerce Spring Boot 示例

面向多商家的实体编程示例：商家维护自己的商品，客户一次结算可购买多个商家的商品；服务端按商家拆单，并保存商品名称、成交价和金额快照。

> CRUD 2.0 重构进行中；部分旧示例和测试仍待迁移，当前能力以实施清单为准。

## 启动

使用 JDK 21、MySQL 8+ 和 Docker Compose v2.20+。示例依赖尚未发布的 `2.0.0-SNAPSHOT`，先在 `ent-loom` 仓库根目录安装当前工作区构件：

```bash
./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter,:ent-loom-ddl-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install
```

再进入本示例目录：

```bash
cp .env.example .env
docker compose up -d --wait mysql
../../mvnw spring-boot:run
```

Windows 使用 `Copy-Item .env.example .env` 和 `../../mvnw.cmd spring-boot:run`。默认应用端口为 **8082**，数据库端口为 **3308**；连接配置见 `.env.example`。

开发配置使用 `CREATE_MODIFY_DELETE_ALL`，自动建表并同步结构，可能删除多余列及其数据。需保留旧结构时使用 `CREATE_MODIFY_TABLE_AND_METAS`；生产结构变更使用版本化迁移。

## 演示

启动后准备客户 301/302 和商家商品：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/merchant-demo.sql
```

按以下请求文件体验业务，请求中已包含令牌和载荷：

| 场景 | 请求文件 |
| --- | --- |
| 客户、商品、跨商家下单与成交快照 | [commerce.http](requests/commerce.http) |
| 客户创建、更新、分页、详情及删除拒绝 | [crud.http](requests/crud.http) |
| 商家商品隔离、批量改价、乐观锁与回滚 | [merchant.http](requests/merchant.http) |
| 客户与商家共用订单分页入口 | [order-query.http](requests/order-query.http) |
| Meta 实体契约与主体授权 | [documentation.http](requests/documentation.http) |

订单分页也可单独准备演示数据：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/order-query-demo.sql
```

| 演示 Token | 服务端绑定身份 |
| --- | --- |
| `demo-customer-a` / `demo-customer-b` | 客户 `301` / `302` |
| `demo-merchant-a` / `demo-merchant-b` | 商家 `1001` / `2001` |
| `demo-platform-admin` | 平台管理员 |

正式应用应替换演示认证与身份归属来源，并补齐库存、支付和发货等业务能力。

## 业务与代码

代码位于 `src/main/java/com/example/minicommerce/`，按 `customer`、`merchant`、`order` 分包；公共异常和 HTTP 错误处理放在 `common`。

| 实体 | 职责 |
| --- | --- |
| `CustomerBankCard` | 用户本人维护银行卡绑定信息，商家无权访问；不保存完整卡号 |
| `Customer` | 平台客户主数据 |
| `CustomerLevel` | 用户在商家的会员等级：商家维护本店记录，用户只读自己的记录 |
| `Merchant` | 匿名及顾客只读商家 ID、名称；商家仅查看和修改本人资料，超级管理员可管理全部 |
| `MerchantProduct`、`MerchantArticle` | 商家商品与文章，维护复用通用 CRUD |
| `Order` | 客户与商家共用的订单，记录归属、状态和金额 |
| `OrderItem` | 订单明细与成交快照，不开放独立 HTTP |

- **下单**：[PlaceOrderHandler](src/main/java/com/example/minicommerce/order/handler/PlaceOrderHandler.java)，入口 `POST /api/ent-crud/order/action/create`。客户只提交商品 ID 和数量；客户归属来自可信身份，商家归属来自商品。同商家合并、跨商家拆单，订单和明细在同一事务内保存。
- **取消**：`POST /api/ent-crud/order/action/cancel`，客户与商家只能取消所属且状态为 `CREATED` 的订单。
- **查询**：订单及商家商品等实体直接复用 `list/page/findOne/detail`，无需业务 Controller 或查询 Service。普通筛选放在 `options`，详情使用顶层 `id`；分页返回 `data.items/data.page`。
- **商品摘要**：`POST /api/merchant/products/page` 接受 `{"page":1,"limit":20,"active":true}`，返回 `id/name/price` DTO。商家与管理员每次重新授权同一 `merchantProduct/view`；商家范围来自授权结果，列表与总数使用相同范围和筛选。直接使用 JDBC 的业务 Mapper 负责落实固定投影、参数化条件和范围，无法表达的授权范围在 SQL 前拒绝。
- **关联详情**：订单查询默认只读当前表，显式传入 `expandRelations: ["customer", "orderItemList"]` 才读取客户与明细；成交快照不重新读取当前商品。

业务 Handler 通过 `AuthorizedActionContext` 获取可信身份，并创建受授权范围约束的 `EntityDao`，无需空 DAO 接口。数据隔离、字段限制和错误响应见 [访问控制](docs/access-control.md)。

## 配置入口

| 位置 | 职责 |
| --- | --- |
| [application.yml](src/main/resources/application.yml) | 数据源、元数据、共享访问策略、DDL 与实体文档 |
| [demo.yml](src/main/resources/demo.yml) | 演示账号、可信身份绑定、入口路由与审计 |
| [ent/entities](src/main/resources/ent/entities) | 各实体的权限差异、字段、动作、输入契约与索引 |

Java 保留字段、关系事实和业务处理，YAML 集中声明实体能力。例如 `Order` 使用 `workflow` 并增加下单与取消，`MerchantProduct` 继承 `maintainable` 并增加批量更新。

文章正文通过 `@EntField(value = "正文", kind = EntFieldKind.CONTENT)` 表达业务语义，未声明业务上限时推导为 TEXT；格式与容量仍需业务约束。

框架规则按需查阅：[配置参考](../../docs/guides/配置参考.md)、[受授权关联读取](../../docs/guides/crud/受授权关联读取.md)、[Java 策略与统一绑定](../../docs/guides/crud/Java策略与统一绑定.md)、[DDL 迁移安全边界](../../docs/guides/ddl/自动迁移安全边界.md)。

## 验证

在本示例目录使用 JDK 21 执行：

```bash
../../mvnw clean test
```

默认使用 H2 固定表结构，关闭启动 DDL，覆盖数据隔离、字段保护、批量更新、拆单、快照、取消和事务回滚。

商品摘要协同路径可单独验证：`../../mvnw -Dtest=ProductSummaryServiceTest,ProductSummaryIntegrationTest test`。

真实 MySQL 的自动建表、金额类型、索引及业务验收见 [MySQL 8 验收](docs/mysql-acceptance.md)。验收必须使用专用空库，会清理其中的业务数据。
