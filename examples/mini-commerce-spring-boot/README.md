# Mini Commerce Spring Boot 示例

这个示例演示一条最小业务协作路径：`Product` 和 `Customer` 使用 ent-loom 通用 CRUD 维护，并通过只读实体文档契约查看公开主数据；有事务和业务不变量的下单动作进入 `PlaceOrderService`，订单及明细通过业务 Controller 查询。

## 环境

- JDK 21+
- Maven 3.9+
- 本机 MySQL 8.0+（日常开发）
- Docker Desktop，包含 Docker Compose v2.20+（干净验收）
- Python 3.9+（非交互验收，仅使用标准库；Windows 可通过 PowerShell 入口执行）
- Spring Boot 3.5.16
- ent-loom 1.0.1，当前 DAO 版本使用工作区构件（见下方验证步骤）

本示例是独立 Maven 消费者，不继承 ent-loom 内部父 POM，不依赖 `ent-loom-tests` 或内部实现类。完整源码位于 `examples/mini-commerce-spring-boot`，POM 声明公开 Starter/API/Annotations、CRUD Core 的 DAO 合同和 Spring Boot 基础依赖。当前 DAO 重构版须使用下方当前工作区构件路径验证；2026-09-08 对 Maven Central `1.0.1` 的验收属于重构前版本，不能作为当前 DAO 能力已发布的依据。

2026-09-18 已使用 JDK 21 和隔离 Maven 仓库完成工作区完整 Reactor 构建、示例 clean/test/package（10 项测试）及真实 MySQL HTTP 验收，覆盖下单、详情、价格快照、失败回滚和生产入口授权。

## 业务边界

- `Product`、`Customer`：实体文档契约和通用 CRUD，分别对应 `/api/ent-crud/product/*` 和 `/api/ent-crud/customer/*`。
- `POST /orders`：接收 `PlaceOrderCommand`，由 `PlaceOrderService` 在一个事务中读取客户和商品、校验商品有效性、复制价格快照并写入 `commerce_order` 与 `commerce_order_item`。
- `GET /orders/{id}`：经 `OrderQueryService` 查询订单头和明细；订单不进入通用 CRUD 白名单。
- 下单和详情共用 `OrderAccessPolicy`，使用当前主体与 `order` 资源的 `PLACE`、`DETAIL` 权限规则；未匹配或明确拒绝时返回 `403 ORDER_ACCESS_DENIED`。订单权限独立于商品、客户 CRUD 权限，授予 `PLACE` 即允许业务流程读取下单所需主数据。
- 示例只演示动作授权；开发者可查看全部示例订单，不演示订单归属、租户隔离或行级数据权限。生产项目使用自有 JDBC 查询时须显式落实这些限制，不能认为 CRUD 数据范围会自动应用。
- 明确排除登录、支付、库存扣减、优惠券、搜索、消息队列和前端管理台。

## 代码组织

按业务分包，订单业务内部再按职责组织：

```text
com.example.minicommerce/
├── MiniCommerceApplication.java
├── configuration/          # 示例治理装配
├── customer/               # entity/ 客户实体，dao/ 客户业务 DAO
├── product/                # entity/ 商品实体，dao/ 商品业务 DAO
└── order/
    ├── controller/         # HTTP 入口和异常响应
    ├── service/            # 下单、查询服务，负责业务校验与事务编排
    ├── dto/                # 命令、结果和详情
    ├── entity/             # Order、OrderItem，分别映射订单表和明细表
    ├── repository/         # 订单聚合的专用 SQL Repository
    ├── enums/              # 订单生命周期状态
    ├── exception/          # 订单业务异常
    └── security/           # 订单动作及主体权限检查
```

调用方向为 `controller -> service -> 业务 DAO / 专用 Repository`，业务 DAO 内部使用 scoped EntityDao，服务先经 `security` 授权，再执行业务校验和数据访问。`PlaceOrderService` 负责下单事务，`OrderQueryService` 负责详情查询。Repository 不依赖 Controller；用例 DTO 直接作为 HTTP 输入输出，暂不增加重复的 Request/Response、转换层或 Repository 接口。

`Customer`、`Product` 是框架管理的实体，主数据维护仍由通用 CRUD 承担。业务 DAO 只需声明 `@EntDao public interface CustomerDao extends EntityDao<Customer, Long> {}`；`ProductDao.findForOrder` 以 `@EntQuery` 读取下单所需投影，下单服务构造器直接注入并调用它，再负责商品状态校验。Starter 默认扫描应用包，也可用 `@EntDaoScan(basePackageClasses = CustomerDao.class)` 指定扫描范围。接口继承基础 CRUD，可通过 `default` 方法组合调用；不支持的方法声明会在启动时失败。

`DaoConfiguration` 提供必需的 `EntityDaoScopeResolver`，本示例显式使用全量主数据范围。代理每次 CRUD 调用解析范围并创建 scoped EntityDao，单例不缓存请求范围；生产项目须接入可信租户或组织上下文，不能从 HTTP 参数直接接受访问范围。缺少解析器、实体元数据或主键类型不匹配时启动失败。

Spring JDBC 事务通过线程绑定复用连接，DAO 调用自动参与 Service 事务；事务上下文不负责解析数据权限。本示例不另建 ThreadLocal 权限上下文，也不注册可直接注入的裸 EntityDao。

`Order`、`OrderItem` 与主数据实体采用相同的字段、中文注释和元数据注解风格，分别映射 `commerce_order`、`commerce_order_item`，明确声明数据库生成主键。当前元数据注册列表仍仅包含 `Product`、`Customer`；订单实体通过 `@EntDao` 扫描代理按需注册，不因添加实体注解自动开放通用 HTTP CRUD。

`PlaceOrderService` 是新增订单聚合的事务边界：先通过 `OrderDao` 写入订单并回填数据库生成主键，再逐条通过 `OrderItemDao` 写入明细并回填各自主键。任一明细失败时，订单和已写入明细由 Spring 事务整体回滚。详情查询由 `OrderQueryService` 分别读取订单、客户和明细后组装，DAO 保持单表职责。

下单内部直接读取 `Customer`、`Product`，组装 `Order` 和 `OrderItem`；商品名称、单价和明细金额作为快照字段保存在 `OrderItem` 中，不再维护重复的快照模型。详情接口继续使用 `OrderDetail` DTO 表达跨表查询结果。`record` 仅用于表达不可变数据载体，不代表独立架构层；按实际职责归包，不额外引入 BO/PO/VO 层。

## 日常开发

首次配置时先安装包含 DAO 能力的工作区构件，再创建本地环境文件和数据库账号。以下命令在本示例目录执行；若构件安装在隔离 Maven 仓库，运行 Maven 时同时传入 `-Dmaven.repo.local=实际仓库路径`。真实凭据只写入被 Git 忽略的 `.env`。

```powershell
Copy-Item .env.example .env
mysql -u root -p -e "create database if not exists mini_commerce; create user if not exists 'mini_commerce'@'localhost' identified by 'mini_commerce_dev'; alter user 'mini_commerce'@'localhost' identified by 'mini_commerce_dev'; grant all privileges on mini_commerce.* to 'mini_commerce'@'localhost'; flush privileges;"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` profile 默认连接 `localhost:3306`，并执行 `schema.sql`。健康检查地址为 [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)。开发态示例还会以 `local-developer` 主体访问 [实体文档契约](http://localhost:8082/api/ent-doc/contract)，只公开 `product` 和 `customer` 主数据。也可以在 IDEA 中以 `com.example.minicommerce.MiniCommerceApplication` 启动，激活 `dev` profile，工作目录设为本示例目录。

## 生产认证适配边界

`production` profile 只演示接入边界，不提供认证系统。`ServletPrincipalCrudSubjectResolver` 读取 Servlet 容器已经完成的 `HttpServletRequest#getUserPrincipal()`，不解析请求头、令牌或登录凭据；真实项目应由 Spring Security、网关或容器完成认证，再把主体交给 ent-loom 治理链。

未认证请求会映射为 `anonymous` 主体。生产 profile 默认没有该主体的权限规则，实体文档契约返回空目录，CRUD 和订单业务入口返回拒绝。设置 `ENTLOOM_PRODUCTION_SUBJECT_ID` 只用于本示例演示一个已认证主体的配置入口，生产项目应使用配置中心、权限服务和自身的租户/组织数据范围策略替换它。

## Compose 验收

手动启动 Compose MySQL 时，端口默认为 `3308`：

```powershell
Copy-Item .env.example .env
docker compose up -d --wait mysql
docker compose ps
```

非交互验收会创建唯一 Compose 项目、数据卷和随机端口，不读取本机 `.env`，也不会占用 `3306` 或 `8082`：

```bash
python3 scripts/verify.py
```

Windows PowerShell：

```powershell
./scripts/verify.ps1
```

脚本依次执行：

1. 构建并启动独立消费者。
2. 通过通用 CRUD 创建商品和客户。
3. 通过 `POST /orders` 完成下单，再通过 `GET /orders/{id}` 查询详情。
4. 修改商品价格，确认订单仍返回下单时的价格快照。
5. 验证失效商品、不存在商品和不存在客户均返回明确失败码。
6. 在独立验收库增加临时明细约束，使订单头写入后明细写入失败，确认事务回滚、订单和明细均无残留，并移除约束。
7. 使用 MySQL SQL 查询核对订单头、订单明细和金额。
8. 启动 `production` profile，确认未认证主体只能获得空实体契约，不能调用通用 CRUD 或订单业务入口。

业务权限测试在本示例目录执行 `mvn test`，覆盖无规则、明确拒绝、非授权主体、缺少主体、默认主体解析失败和动作权限隔离。该测试已接入商城 CI 作业。

无论成功或失败，脚本都会停止应用并执行 `docker compose down --volumes --remove-orphans`；失败时日志保留在 `target/verification-logs/<项目名>/`。

## 验证请求

可复制请求见 [`requests/commerce.http`](requests/commerce.http)。最短业务请求如下：

```bash
curl http://localhost:8082/api/ent-doc/contract
```

```bash
curl -X POST http://localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":2001,"items":[{"productId":1001,"quantity":2}]}'

curl http://localhost:8082/orders/1
```

预期下单结果包含 `orderId` 和 `totalAmount`；详情中的 `items[0].unitPrice` 是订单价格快照，不随商品当前价格变化。

## 使用当前工作区构件验证

POM 版本仍为 `ent-loom 1.0.1`；当前重构依赖工作区 DAO 合同，不能仅凭同一版本号假设 Maven Central 已包含该能力。从 ent-loom 仓库根目录安装到隔离 Maven 仓库，再执行本示例脚本：

```bash
repo=$(mktemp -d)
./mvnw -B "-Dmaven.repo.local=$repo" -DskipTests -Dmaven.javadoc.skip=true install
cd examples/mini-commerce-spring-boot
python3 scripts/verify.py --maven-repository "$repo"
```

这条路径只替换 `ent-loom` 构件来源，示例仍然是独立消费者；不会把示例加入默认主 Reactor。

## 订单 DAO 回归验证

使用 JDK 21，在仓库根目录先运行 `./mvnw -pl :ent-loom-crud-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install`，再在本示例目录运行 `../../mvnw test`（Windows 使用对应的 `mvnw.cmd`）。

`OrderDaoIntegrationTest` 使用 H2 MySQL 模式、真实 `@EntDao` 扫描代理、JDBC 执行器与 Spring 服务事务，验证生成主键回填、下单详情，以及第二条明细违反数据库约束时订单和首条明细全部回滚。测试自身不添加事务，避免掩盖业务服务事务失效；此快速回归不替代 MySQL 8 实例验收。

## 停止和清理

日常开发停止应用即可。本示例手动 Compose 环境的清理命令为：

```powershell
docker compose down
docker compose down -v
```

第二条命令会删除本示例的 MySQL 数据卷。开发态主体、全量数据范围和日志审计配置只适合本地演示；生产项目应接入真实认证主体、权限服务、数据范围和审计存储，并使用正式数据库迁移工具替代 `schema.sql`。

## 实体必填约定

本示例采用 [实体必填约束与统一校验](../../docs/evolution/decisions/core/实体必填约束与统一校验.md)：Customer/Product 将业务字段的必填约束直接声明在字段上，生成 ID 以字段 `FALSE` 声明创建输入例外。框架按类型检查字符串非空白、数组/集合/Map 非空、其他值非 null；0 和 false 合法，文案默认由 label 生成。

默认值、系统赋值和必填约束独立，先在相应阶段赋值再校验；生成主键不要求客户端输入。局部更新区分未传与显式清空。Doc/UI 提示覆盖不能修改服务端规则，DDL 可空性单独声明。

当前创建最小闭环已完成：Meta 约束投影到 CRUD Runtime Model，强类型 Handler 和默认 JDBC 创建统一校验，批量创建复用 CREATE 子命令；Customer/Product 已移除重复的 `NotNull/NotBlank`。更新显式清空、业务默认值统一赋值和结构化错误仍按路线图演进。

`PlaceOrderCommand/PlaceOrderItem` 属于业务输入 DTO，可继续使用 Validation；订单状态、快照及计算金额在对应业务赋值阶段保证有效，不要求客户填写。实施进度见 [Meta 路线图](../../docs/evolution/roadmap/meta/index.md)。
