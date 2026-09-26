# Mini Commerce Spring Boot 示例

最小业务闭环：Product、Customer 使用 ent-loom 通用 CRUD；订单通过 PlaceOrderHandler 在事务中下单，通过默认通用查询读取详情并展开客户、明细。订单保存商品名称、单价和金额快照。

## 业务边界

所有业务入口复用框架内置 Controller。主数据接口为 `/api/ent-crud/product/*`、`/api/ent-crud/customer/*`；下单为 `POST /api/ent-crud/order/action/place`，订单详情为 `POST /api/ent-crud/order/detail`，文档契约为 `/api/ent-doc/contract`。订单通过 `order:read` 授权默认读取，通过 `order:place` 授权下单；直接创建、修改、删除不授权，业务动作仅允许 `place`。示例不包含登录、租户/行级权限、支付、库存、优惠券、搜索、消息队列和前端管理台。

代码按 configuration、customer、product、order 分包。下单调用方向为 `内置 Controller → Gateway → 统一治理与场景分发 → Handler → DAO`；默认查询经 Gateway 治理后由查询引擎执行。应用只实现实体、必要的 DTO、Handler 和治理配置。Gateway 负责授权，下单 Handler 负责业务校验、事务和结果组装。只有需要兼容独立 URL、HTTP 状态码或外部协议时，才增加业务 Controller。

| 业务场景 | 接口 | 框架能力 |
| --- | --- | --- |
| 商品、客户维护 | `/api/ent-crud/{entity}/*` | 默认 CRUD，无需业务 Handler |
| 可售商品分页 | `POST /api/ent-crud/product/page/saleable` | Handler 追加 `active=true`，委托默认引擎过滤、排序、分页 |
| 下单 | `POST /api/ent-crud/order/action/place` | ACTION 强类型入出参契约、跨 DAO 事务与价格快照 |
| 订单详情 | `POST /api/ent-crud/order/detail` | 默认 DETAIL 查询，通过 `expandRelations` 展开客户和订单明细 |

`Order` 通过 `@EntCrudActions("place")` 限制业务动作，`OrderSceneConfiguration` 声明下单 ACTION 场景策略。订单注册到 HTTP 实体路由，但不进入实体文档白名单；示例权限配置向 `local-developer` 授予订单读取与下单权限。示例使用全量数据范围，未限制订单归属；生产环境需接入正式的数据范围实现。

详情请求使用 `options.resultMode: "ENTITY"` 保持实体字段名，通过 `options.expandRelations: ["customer", "orderItemList"]` 展开关联，返回 `data.item.customer.displayName` 和 `data.item.orderItemList`；不展开时只读取订单本身。明细中的商品名称、成交单价仍来自下单快照。详情未命中使用框架默认的 HTTP 404 与 `ROUTE_NOT_FOUND` 错误码，附加过滤条件由默认引擎统一执行。

可售条件与调用方过滤条件取交集：传入 `active=false` 返回空页；普通商品管理分页仍可查看停用商品。可复制请求见 [commerce.http](requests/commerce.http)。


## 验收与运行

本目录需 JDK 21+、Maven 3.9+、MySQL 8+；Compose 验收使用 Docker Compose v2.20+ 和 Python 3.9+。本地执行：

```powershell
Copy-Item .env.example .env
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

接口包括主数据 CRUD、订单下单与默认查询，以及 `/api/ent-doc/contract`。订单直接创建、修改、删除不授权。

非交互验收：

```bash
python3 scripts/verify.py
# Windows: ./scripts/verify.ps1
```

脚本覆盖主数据创建、可售商品过滤、下单、详情、价格快照、失败回滚和 SQL 核对。运行 `../../mvnw test` 可执行 H2 MySQL 模式回归，覆盖真实场景注册、授权拒绝、分页委托及第二条明细失败时整体回滚；测试本身不包裹事务。

使用当前工作区构件（版本号相同不代表 Maven Central 已包含这些能力）：先在仓库根目录使用 JDK 21 执行 `./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install`，再执行示例测试或验收脚本。隔离仓库安装时，脚本需同时传入 `--maven-repository <临时目录>`。

商品、客户、订单和明细统一使用数据库生成主键，实体声明与 MySQL `auto_increment` 表结构保持一致。Customer/Product 必填约束由实体元数据驱动；PlaceOrderCommand 为不可变 record，Handler 校验业务输入。停止 Compose：docker compose down（加 -v 删除数据卷）。生产环境请接入正式认证、权限、数据范围、迁移和审计。

实体与表名统一采用默认命名：`Order → order`、`OrderItem → order_item`，不额外配置 `entity` 或 `table`。`order` 是 SQL 关键字，手写 SQL 使用反引号引用；框架生成的 SQL 自动引用标识符。

## 治理默认装配与定制

`application-example.yml` 只保留 P1、少量 P2 和必要的安全边界。它显式开启 `ent.loom.crud.example.enabled`，由 Starter 提供独立的演示主体和数据范围默认实现。实体通过 `meta.base-packages` 扫描，路由使用 `controller.exposure-mode: ALL_REGISTERED`；`OrderItem` 通过 `@EntCrudEntity(httpExposed = false)` 关闭独立接口，订单操作仍由权限规则约束；文档通过 `doc.contract.exposure` 声明主体与字段白名单，无需额外配置类。全部公开配置、默认值和分级见[配置参考](../../docs/guides/配置参考.md)；自定义同类型 Bean 可分别覆盖主体、数据范围、注册表或文档策略，详见[默认装配与定制](../../docs/guides/默认装配与定制.md)。
