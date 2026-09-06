# Customer Profile Spring Boot 示例

这个示例只演示一条可复制的最短路径：普通 Spring Boot 项目声明一个 `CustomerProfile`，由公开的 ent-loom Starter 暴露 CRUD HTTP 接口，在 MySQL 8 上完成 `CREATE -> DETAIL`。

## 环境

- JDK 21+
- Maven 3.9+
- 本机 MySQL 8.0+（日常开发）
- Docker Desktop，包含 Docker Compose v2.20+（干净验收）
- Spring Boot 3.5.16
- ent-loom 1.0.0，来自 Maven Central

示例的 POM 是独立消费者，不继承 ent-loom 内部父 POM，也不依赖 `ent-loom-tests` 或内部实现类。实体只使用公开的 Meta 和 CRUD 注解；没有 Lombok。

## 日常开发

日常开发时，Spring Boot 在本机或 IDE 中运行，连接本机 MySQL，便于断点调试。先准备一个本地数据库和开发账号；如果已有账号，修改 `.env` 中的 `DEV_MYSQL_*` 配置即可。

```powershell
Copy-Item .env.example .env
mysql -u root -p -e "create database if not exists customer_profile; create user if not exists 'customer_profile'@'localhost' identified by 'customer_profile_dev'; alter user 'customer_profile'@'localhost' identified by 'customer_profile_dev'; grant all privileges on customer_profile.* to 'customer_profile'@'localhost'; flush privileges;"
```

启动应用：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` profile 默认连接 `localhost:3306`，并执行 `schema.sql`。IDE 运行 `CustomerProfileApplication` 时，将 active profile 设为 `dev`，工作目录设为示例目录即可。需要远程调试时可以使用：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

`dev` 和 `verify` profile 会自动启用同一组 ent-loom 示例配置；基础配置默认关闭 SQL 初始化，避免把本地 DDL 带入其他运行环境。

## Compose 验收环境

Compose 只负责提供干净的 MySQL，应用仍在宿主机运行。

```powershell
Copy-Item .env.example .env
docker compose up -d --wait mysql
docker compose ps
```

默认映射到本机 `3307` 端口，数据库和账号只用于验收。Compose 会创建一个独立的 `customer-profile-mysql` 数据卷。

也可以直接执行非交互验收脚本。脚本会构建应用、启动 Compose MySQL、等待健康状态、以 `verify` profile 启动应用，执行 `CREATE` 和 `DETAIL`，再用 SQL 校验关键字段：

```powershell
./scripts/verify.ps1
```

示例显式配置了：

- `CustomerProfile` 的实体类名和 `customer_profile` 实体白名单；
- CRUD Query、Command 和默认 Controller 开关；
- `local-developer` 开发态主体；
- 仅允许该主体访问该实体的开发态权限规则；
- 开发态全量数据范围和日志审计。

应用健康状态可在 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) 查看。

## 验证请求

请求文件见 [`requests/customer-profile.http`](requests/customer-profile.http)。框架 HTTP Controller 使用 `POST`，详情查询通过 `options.filterMap.id` 定位刚创建的记录：

```powershell
$create = Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/ent-crud/customer_profile/create `
  -ContentType application/json `
  -Body '{"options":{"requestId":"customer-profile-create-1"},"payload":{"id":1000000000001,"displayName":"Ada Lovelace","email":"ada@example.com"}}'

$id = $create.data.id
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/ent-crud/customer_profile/detail `
  -ContentType application/json `
  -Body (ConvertTo-Json @{ options = @{ requestId = "customer-profile-detail-1"; filterMap = @{ id = @{ op = "EQ"; value = $id } } } } -Depth 8)
```

框架源码构建验证时，在仓库根目录先安装到隔离仓库，再传给脚本：

```powershell
$repo = Join-Path $env:TEMP "ent-loom-example-m2"
../../mvnw.cmd -Dmaven.repo.local=$repo -DskipTests install
./scripts/verify.ps1 -MavenRepository $repo
```

## 停止和清理

日常开发时停止应用即可；本机 MySQL 按本机服务管理方式停止。

```powershell
docker compose down
docker compose down -v
```

第二条命令会删除这个示例的 MySQL 数据卷。开发态主体、全量数据范围和日志审计配置只适合本地演示；生产项目应接入真实认证主体、权限服务、数据范围和审计存储，并使用正式数据库迁移工具替代 `schema.sql`。
