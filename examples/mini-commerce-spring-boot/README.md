# Mini Commerce Spring Boot 示例

面向多商家的实体编程示例：商家维护自己的商品，客户一次结算可购买多个商家的商品；服务端按商家拆单，并保存商品名称、成交价和金额快照。

> CRUD 2.0 重构进行中；部分旧示例和测试仍待迁移，当前能力以实施清单为准。

## 两种 CRUD 接入方式

| 场景 | 实体 | HTTP 入口 | 业务代码 |
| --- | --- | --- | --- |
| 自定义 Controller，复用 CRUD 内核 | `MerchantArticle` | `/api/merchant/articles` | Controller 使用实体、摘要 DTO 和类型化 Patch，调用 CRUD 内核 |
| 通用 CRUD 全流程，声明轻度复杂规则 | `MerchantProduct` | `/api/ent-crud/merchantProduct/*` | 实体注解与全局配置；不编写商品 Controller、查询 Service 或 SQL |

文章关闭通用 HTTP，通过自定义路由、扁平写入载荷和固定列表投影体现接口定制；授权、归属填充、字段校验、查询计数、写入事务与审计仍由内核执行。商品通过声明实现商家隔离、公开上架商品、字段白名单、金额校验、乐观锁与批量改价。

完整对照见 [两种 CRUD 接入演示](docs/两种CRUD接入演示.md)。

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
| 商户文章自定义分页、详情和增改删，复用 CRUD 内核 | [article.http](requests/article.http) |
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
| `MerchantArticle` | 自定义 Controller 调用 CRUD 内核；关闭通用 HTTP |
| `MerchantProduct` | 完整通用 CRUD，通过注解声明轻度复杂业务规则 |
| `CommerceOrder` | 客户与商家共用的订单，记录归属、状态和金额 |
| `CommerceOrderItem` | 订单明细与成交快照，不开放独立 HTTP |

- **下单**：[PlaceOrderHandler](src/main/java/com/example/minicommerce/order/handler/PlaceOrderHandler.java)，入口 `POST /api/ent-crud/commerceOrder/action/create`。客户只提交商品 ID 和数量；客户归属来自可信身份，商家归属来自商品。同商家合并、跨商家拆单，订单和明细在同一事务内保存。
- **取消**：`POST /api/ent-crud/commerceOrder/action/cancel`，客户与商家只能取消所属且状态为 `CREATED` 的订单。
- **查询**：订单及商家商品等实体直接复用 `list/page/findOne/detail`，无需业务 Controller 或查询 Service。通用 HTTP 支持动态字段投影，使用动态记录承接授权字段；普通 Java 业务查询默认使用实体或明确 DTO。普通筛选放在 `options`，详情使用顶层 `id`；分页返回 `data.items/data.page`。
- **文章自定义接口**：[MerchantArticleController](src/main/java/com/example/minicommerce/merchant/web/MerchantArticleController.java) 直接调用查询和命令网关，不经通用 Controller，也不自行拼 SQL 或转换授权范围。请求见 [article.http](requests/article.http)。
- **关联详情**：订单查询默认只读当前表，显式传入 `expandRelations: ["customer", "orderItemList"]` 才读取客户与明细；成交快照不重新读取当前商品。

文章自定义分页返回 `CrudResponse<PageResult<MerchantArticleSummary>>`，DTO 自动声明 `id/title` 投影；详情返回 `MerchantArticle`。创建接收专用 DTO，部分更新接收 `UpdatePatch<MerchantArticle>`，写入返回 `EntityWriteResult<Long>`。通用商品分页使用框架 HTTP 契约 `data.items/data.page`；两种接口响应结构由各自 Controller 决定，底层复用同一 CRUD 授权与执行链。

业务 Handler 通过 `AuthorizedActionContext` 获取可信授权，订单命令服务组合 MyBatis-Plus 单实体服务并落实范围、状态与事务。原始 Mapper 不自动继承框架的数据权限；数据隔离、字段限制和错误响应见 [访问控制](docs/access-control.md)。

## 配置入口

主键统一使用数据库自增：`ent.loom.meta.defaults.id-policy: DATABASE` 与 `mybatis-plus.global-config.db-config.id-type: auto` 配套配置，实体的 `id` 字段无需声明 `@TableId`。

| 位置 | 职责 |
| --- | --- |
| [application.yml](src/main/resources/application.yml) | 数据源、元数据扫描、DDL、实体文档，以及身份结构 |
| [demo.yml](src/main/resources/demo.yml) | 演示账号、可信身份绑定、入口路由与审计 |
| 实体 Java 类与 Handler | 实体元数据、权限差异、字段边界、动作和输入契约 |

项目通过 `ent.loom.crud.defaults.actions` 提供标准 CRUD 动作上限；单实体专属授权通过实体上的 `@EntCrudGrant` 就地声明，`@EntCrudEntity` 保留动作、字段和输入约束。通用资源类别基线放在 [crud-policies.yml](src/main/resources/crud-policies.yml)：银行卡选择 `customer-owned`，会员等级选择 `merchant-managed-customer-readable`。商家、客户主数据、商品和订单的专属规则就地声明。动态流程放在 Handler / Service。`application.yml` 的 `grant-defaults` 提供商家/客户归属与管理员默认权限；订单显式限制管理员为 `view/cancel`。详见 [授权整合最佳实践](docs/授权整合最佳实践.md)。

本示例通过 Meta 集成复用 `@EntEntity` 生成 DDL 元数据，无需重复声明空的 `@EntDdlEntity`，也无需额外 YAML 开关；仅在需要 schema、命名策略、表规模等 DDL 专属定制时添加 `@EntDdlEntity`。`@EntDdlField`、`@EntDdlIndex` 可按需独立使用。普通索引省略 `name`，框架按物理表名和有序列名自动命名，超长时截断并追加稳定摘要。

文章正文通过 `@EntField(value = "正文", kind = EntFieldKind.CONTENT)` 表达业务语义，未声明业务上限时推导为 TEXT；格式与容量仍需业务约束。

框架规则按需查阅：[实体优先的类型化 CRUD](../../docs/guides/crud/实体优先的类型化CRUD.md)、[配置参考](../../docs/guides/配置参考.md)、[受授权关联读取](../../docs/guides/crud/受授权关联读取.md)、[Java 策略与统一绑定](../../docs/guides/crud/Java策略与统一绑定.md)、[DDL 迁移安全边界](../../docs/guides/ddl/自动迁移安全边界.md)。

## 验证

在本示例目录使用 JDK 21 执行：

```bash
../../mvnw clean test
```

默认使用 H2 固定表结构，关闭启动 DDL，覆盖数据隔离、字段保护、批量更新、拆单、快照、取消和事务回滚。

两种接入方式可单独验证：`../../mvnw -Dtest=MerchantArticleIntegrationTest,MerchantProductUpdateIntegrationTest test`。

真实 MySQL 的自动建表、金额类型、索引及业务验收见 [MySQL 8 验收](docs/mysql-acceptance.md)。验收必须使用专用空库，会清理其中的业务数据。

## 服务目录约定

按业务模块组织服务：实体服务放在 `service/`，查询、摘要与统计服务放在 `service/query/`，独立的业务命令与事务编排服务放在 `service/command/`。仅在存在对应服务时创建子包；包分类不强制增加调用层级。

文章的简单 CRUD 直接由 Controller 调用内核，无需转发 Service；商品的 MyBatis-Plus Mapper / Service 仅供下单流程读取商品，不参与通用 CRUD。订单下单与取消由 `order/service/command/CommerceOrderCommandService.java` 承担。订单 Handler 只负责动作接入、传递可信授权上下文与包装结果；命令服务统一负责业务规则、跨实体协作与事务，包分类不额外增加转发层。
