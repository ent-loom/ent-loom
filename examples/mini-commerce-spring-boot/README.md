# Mini Commerce Spring Boot 示例

最小业务闭环：Product、Customer 使用 ent-loom 通用 CRUD；订单通过 PlaceOrderHandler 在事务中下单，通过 OrderDetailHandler 查询详情。订单保存商品名称、单价和金额快照。

## 业务边界

所有业务入口复用框架内置 Controller。主数据接口为 `/api/ent-crud/product/*`、`/api/ent-crud/customer/*`；下单为 `POST /api/ent-crud/order/action/place`，订单详情为 `POST /api/ent-crud/order/detail/detail`，文档契约为 `/api/ent-doc/contract`。权限只放行订单的 `COMMAND:ACTION + place` 与 `QUERY:DETAIL + detail`；订单默认 CRUD 和未知场景均拒绝。示例不包含登录、租户/行级权限、支付、库存、优惠券、搜索、消息队列和前端管理台。

代码按 configuration、customer、product、order 分包。调用方向为 `内置 Controller → Gateway → 统一治理与场景分发 → Handler → DAO`；应用只实现实体、DTO、Handler 和治理配置。Gateway 负责授权，Handler 负责业务校验、事务和结果组装。只有需要兼容独立 URL、HTTP 状态码或外部协议时，才增加业务 Controller。

| 业务场景 | 接口 | 框架能力 |
| --- | --- | --- |
| 商品、客户维护 | `/api/ent-crud/{entity}/*` | 默认 CRUD，无需业务 Handler |
| 可售商品分页 | `POST /api/ent-crud/product/page/saleable` | Handler 追加 `active=true`，委托默认引擎过滤、排序、分页 |
| 下单 | `POST /api/ent-crud/order/action/place` | ACTION 强类型入出参契约、跨 DAO 事务与价格快照 |
| 订单详情 | `POST /api/ent-crud/order/detail/detail` | DETAIL 场景跨实体组装 `OrderDetail` |

`OrderSceneConfiguration` 声明下单 ACTION 场景准入。订单注册到 HTTP 实体路由以供内置 Controller 定位 Handler，但不进入实体文档白名单；示例权限规则只允许 `local-developer` 执行上述两个操作与场景，默认创建、修改、删除、分页及未知场景均失败关闭。示例仍使用全量 DAO 数据范围；自定义查询不会因为进入 Gateway 就自动落实订单归属限制。

可售条件与调用方过滤条件取交集：传入 `active=false` 返回空页；普通商品管理分页仍可查看停用商品。可复制请求见 [commerce.http](requests/commerce.http)。


## 验收与运行

本目录需 JDK 21+、Maven 3.9+、MySQL 8+；Compose 验收使用 Docker Compose v2.20+ 和 Python 3.9+。本地执行：

```powershell
Copy-Item .env.example .env
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

接口包括主数据 CRUD、订单下单与详情场景，以及 `/api/ent-doc/contract`。订单默认 CRUD 不授权。

非交互验收：

```bash
python3 scripts/verify.py
# Windows: ./scripts/verify.ps1
```

脚本覆盖主数据创建、可售商品过滤、下单、详情、价格快照、失败回滚和 SQL 核对。运行 `../../mvnw test` 可执行 H2 MySQL 模式回归，覆盖真实场景注册、授权拒绝、分页委托及第二条明细失败时整体回滚；测试本身不包裹事务。

使用当前工作区构件（版本号相同不代表 Maven Central 已包含这些能力）：先在仓库根目录使用 JDK 21 执行 `./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install`，再执行示例测试或验收脚本。隔离仓库安装时，脚本需同时传入 `--maven-repository <临时目录>`。

商品、客户、订单和明细统一使用数据库生成主键，实体声明与 MySQL `auto_increment` 表结构保持一致。Customer/Product 必填约束由实体元数据驱动；PlaceOrderCommand 为不可变 record，Handler 校验业务输入。停止 Compose：docker compose down（加 -v 删除数据卷）。生产环境请接入正式认证、权限、数据范围、迁移和审计。
