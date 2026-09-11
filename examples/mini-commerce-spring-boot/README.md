# Mini Commerce Spring Boot 示例

这个示例演示一条最小业务协作路径：`Product` 和 `Customer` 使用 ent-loom 通用 CRUD 维护，并通过只读实体文档契约查看公开主数据；有事务和业务不变量的下单动作进入 `PlaceOrderHandler`，订单及明细通过业务 Controller 查询。

## 环境

- JDK 21+
- Maven 3.9+
- 本机 MySQL 8.0+（日常开发）
- Docker Desktop，包含 Docker Compose v2.20+（干净验收）
- Python 3.9+（非交互验收，仅使用标准库；Windows 可通过 PowerShell 入口执行）
- Spring Boot 3.5.16
- ent-loom 1.0.1，来自 Maven Central

本示例是独立 Maven 消费者，不继承 ent-loom 内部父 POM，不依赖 `ent-loom-tests` 或内部实现类。完整源码位于 `examples/mini-commerce-spring-boot`，POM 只声明公开 Starter/API/Annotations 和 Spring Boot 基础依赖。2026-09-08 已使用仅下载公开构件的隔离 Maven 仓库，通过 `ent-loom 1.0.1` 的构建、真实启动、HTTP/MySQL、价格快照和事务回滚验收。修改框架源码时可使用下方当前工作区构件路径。

## 业务边界

- `Product`、`Customer`：实体文档契约和通用 CRUD，分别对应 `/api/ent-crud/product/*` 和 `/api/ent-crud/customer/*`。
- `POST /orders`：接收 `PlaceOrderCommand`，由 `PlaceOrderHandler` 在一个事务中读取客户和商品、校验商品有效性、复制价格快照并写入 `commerce_order` 与 `commerce_order_item`。
- `GET /orders/{id}`：经 `OrderQueryService` 查询订单头和明细；订单不进入通用 CRUD 白名单。
- 下单和详情共用 `OrderAccessPolicy`，使用当前主体与 `order` 资源的 `PLACE`、`DETAIL` 权限规则；未匹配或明确拒绝时返回 `403 ORDER_ACCESS_DENIED`。订单权限独立于商品、客户 CRUD 权限，授予 `PLACE` 即允许业务流程读取下单所需主数据。
- 示例只演示动作授权；开发者可查看全部示例订单，不演示订单归属、租户隔离或行级数据权限。生产项目使用自有 JDBC 查询时须显式落实这些限制，不能认为 CRUD 数据范围会自动应用。
- 明确排除登录、支付、库存扣减、优惠券、搜索、消息队列和前端管理台。

## 代码组织

按业务分包，订单业务内部再按职责组织：

```text
com.example.minicommerce/
├── MiniCommerceApplication.java
├── configuration/          # 示例开发态治理装配
├── catalog/                # 商品、客户通用 CRUD 实体
└── order/
    ├── web/                # HTTP 入口和异常响应
    ├── application/        # 下单、查询入口及命令、结果、业务异常
    ├── model/              # 订单详情、状态和价格快照
    ├── persistence/        # 具体 JDBC Repository
    └── security/           # 订单动作及主体权限检查
```

调用方向为 `web -> application -> persistence/model`，业务入口先经 `security` 授权。Repository 不依赖 Web 响应类型；订单详情模型直接序列化为响应。请求与命令暂时共用，不增加重复 DTO、转换层或 Repository 接口。商品、客户 Repository 放在订单业务中，因为它们只服务于下单读取，主数据维护仍由通用 CRUD 承担。

## 日常开发

首次配置时创建本地环境文件和数据库账号。以下命令在本示例目录执行；真实凭据只写入被 Git 忽略的 `.env`。

```powershell
Copy-Item .env.example .env
mysql -u root -p -e "create database if not exists mini_commerce; create user if not exists 'mini_commerce'@'localhost' identified by 'mini_commerce_dev'; alter user 'mini_commerce'@'localhost' identified by 'mini_commerce_dev'; grant all privileges on mini_commerce.* to 'mini_commerce'@'localhost'; flush privileges;"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` profile 默认连接 `localhost:3306`，并执行 `schema.sql`。健康检查地址为 [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)。开发态示例还会以 `local-developer` 主体访问 [实体文档契约](http://localhost:8082/api/ent-doc/contract)，只公开 `product` 和 `customer` 主数据。也可以在 IDEA 中以 `com.example.minicommerce.MiniCommerceApplication` 启动，激活 `dev` profile，工作目录设为本示例目录。

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

默认 POM 消费 Maven Central 的 `ent-loom 1.0.1`。验证框架源码改动时，从 ent-loom 仓库根目录安装到隔离 Maven 仓库，再执行本示例脚本：

```bash
repo=$(mktemp -d)
./mvnw -B "-Dmaven.repo.local=$repo" -DskipTests -Dmaven.javadoc.skip=true install
cd examples/mini-commerce-spring-boot
python3 scripts/verify.py --maven-repository "$repo"
```

这条路径只替换 `ent-loom` 构件来源，示例仍然是独立消费者；不会把示例加入默认主 Reactor。

## 停止和清理

日常开发停止应用即可。本示例手动 Compose 环境的清理命令为：

```powershell
docker compose down
docker compose down -v
```

第二条命令会删除本示例的 MySQL 数据卷。开发态主体、全量数据范围和日志审计配置只适合本地演示；生产项目应接入真实认证主体、权限服务、数据范围和审计存储，并使用正式数据库迁移工具替代 `schema.sql`。
