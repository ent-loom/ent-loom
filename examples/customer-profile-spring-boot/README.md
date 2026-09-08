# Customer Profile Spring Boot 示例

这个示例只演示一条可复制的最短路径：普通 Spring Boot 项目声明一个 `CustomerProfile`，由公开的 ent-loom Starter 暴露 CRUD HTTP 接口，在 MySQL 8 上完成 `CREATE -> DETAIL`。

## 环境

- JDK 21+
- Maven 3.9+
- 本机 MySQL 8.0+（日常开发）
- Docker Desktop，包含 Docker Compose v2.20+（干净验收）
- Python 3.9+（非交互验收，仅使用标准库；Windows 可通过 PowerShell 入口执行）
- Spring Boot 3.5.16
- ent-loom 1.0.1，来自 Maven Central

示例的 POM 是独立消费者，不继承 ent-loom 内部父 POM，也不依赖 `ent-loom-tests` 或内部实现类。实体只使用公开的 Meta 和 CRUD 注解；没有 Lombok。

POM 默认从 Maven Central 获取 `ent-loom 1.0.1`。该版本修复了 CRUD Starter 的自动配置顺序问题，可在独立 Maven 仓库中完成真实 HTTP/MySQL 验收；修改框架源码时，仍可使用文末的当前工作区构件路径。

## 日常开发

日常开发时，Spring Boot 在本机或 IDE 中运行，连接本机 MySQL，便于断点调试。先准备一个本地数据库和开发账号；如果已有账号，修改 `.env` 中的 `DEV_MYSQL_*` 配置即可。

以下命令均在本示例目录执行。首次配置时，从 `.env.example` 创建 `.env`；已有 `.env` 时直接修改，避免覆盖本机配置。macOS/Linux 使用 `cp .env.example .env`，PowerShell 使用下面的 `Copy-Item`。

```powershell
Copy-Item .env.example .env
mysql -u root -p -e "create database if not exists customer_profile; create user if not exists 'customer_profile'@'localhost' identified by 'customer_profile_dev'; alter user 'customer_profile'@'localhost' identified by 'customer_profile_dev'; grant all privileges on customer_profile.* to 'customer_profile'@'localhost'; flush privileges;"
```

### IntelliJ IDEA / Services 启动

1. 将本示例的 `pom.xml` 添加为 Maven 项目。它是独立消费者，不在框架根 POM 的默认 Reactor 中。
2. 打开 **Run → Edit Configurations**，选择已有的 `Customer Profile (Local Dev)`；没有时新增 Spring Boot 配置。
3. 核对以下配置，再在 **Services** 中选择该配置，点击 Run 或 Debug。

| 配置项 | 值 |
| --- | --- |
| Main class | `com.example.customerprofile.CustomerProfileApplication` |
| Use classpath of module | `customer-profile-spring-boot` |
| JRE | JDK 21 |
| Active profiles | `dev`；已有 VM 参数 `-Dspring.profiles.active=dev` 时无需重复填写 |
| Working directory | 本示例目录，路径规则见下文 |

打开整个 `ent-workspace` 时，工作目录为 `$PROJECT_DIR$/ent-loom/examples/customer-profile-spring-boot`；只打开 `ent-loom` 时为 `$PROJECT_DIR$/examples/customer-profile-spring-boot`；单独打开本示例时为 `$PROJECT_DIR$`。

Services 使用同一份运行配置。若未显示该配置，在 Services 的添加服务入口中选择 **Run Configuration Type → Spring Boot**。停止时点击该应用的 Stop，本机 MySQL 无需停止。

### IDEA 一键验收（macOS / ent-workspace）

打开整个 `ent-workspace` 时，选择共享运行配置 **Customer Profile (Verify)** 并点击 Run。它使用 Shell Script 配置加载本机 zsh 登录环境、选择 JDK 21，再调用同一份 `scripts/verify.py`，无需 Python 插件。首次未显示时从磁盘重新加载项目，并确认 IDEA 的 Shell Script 插件已启用；需要在 Services 中展示时，添加 Shell Script 运行配置类型。

运行前启动 Colima，确保终端能找到 Python 3.9+ 和 Docker Compose，且 `mysql:8.4` 镜像可用。看到“验收通过并完成清理”且退出码为 0 即为成功；失败时查看控制台给出的日志目录。脚本自动完成构建、数据库启动、HTTP 和 SQL 核对、资源清理，无需启动 Manual Verify 应用或修改 `.env`。Colima 保持运行。

日常开发使用 **Customer Profile (Local Dev)**；完整验收使用 **Customer Profile (Verify)**。此入口消费本机 Maven 仓库中的框架构件；修改框架源码后应先安装新构件，隔离仓库验证方式见下文。

### 本机配置与 Git 忽略

`application.yml` 已通过 Spring Boot 原生机制加载工作目录中的 `.env`，无需安装 EnvFile 插件，也无需在 IDEA 中重复填写数据库环境变量：

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

在 `.env` 中配置本机连接，例如：

```properties
DEV_MYSQL_HOST=localhost
DEV_MYSQL_PORT=3306
DEV_MYSQL_DATABASE=customer_profile
DEV_MYSQL_USER=customer_profile
DEV_MYSQL_PASSWORD=customer_profile_dev
DEV_APP_PORT=8080
```

使用普通 `KEY=value` 格式，不加 `export` 或 shell 引号。修改后重启应用生效；IDEA 中显式设置的同名环境变量会覆盖文件值。日常开发建议使用仅有 `customer_profile` 库权限的账号，真实凭据只写入本机 `.env`。

本目录的 `.gitignore` 已忽略 `.env` 和 `target/`，由 Git 自动识别，无需在 Services 中配置。`.env.example` 保留可提交的配置模板；共享运行配置中不填写真实密码。

启动后访问 [健康检查](http://localhost:8080/actuator/health)，应返回 `{"status":"UP"}`，再执行下方的验证请求。

### 命令行启动

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

也可以直接执行非交互验收脚本。脚本优先使用仓库 Maven Wrapper 构建应用，启动 Compose MySQL，等待健康状态，以 `verify` profile 启动应用，执行 `CREATE` 和 `DETAIL`，再用 SQL 校验关键字段。运行前确保 `JAVA_HOME` 指向 JDK 21。

macOS/Linux：

```bash
python3 scripts/verify.py
```

Windows PowerShell：

```powershell
./scripts/verify.ps1
```

两个入口共用 `verify.py`。每次验收使用唯一 Compose 项目、新数据卷和随机端口，不读取本机 `.env`，不占用日常开发的 3306/8080。脚本成功或失败均停止本次应用，并执行 `down --volumes --remove-orphans` 清理本次资源；不会清理其他 Compose 项目。日志和请求响应保留在 `target/verification-logs/<项目名>/`，包括构建、应用、容器、SQL 核对和清理记录。

验收使用固定的临时数据库账号，仅用于自动创建的 MySQL 容器。手动 `docker compose up` 仍使用 `.env` 中的 `MYSQL_*` 配置，与脚本验收相互独立。

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

框架源码构建验证时，先在 **ent-loom 仓库根目录**安装到隔离仓库，再进入示例目录执行脚本。macOS/Linux：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # macOS；Linux 设置本机 JDK 21 路径
example_repo=$(mktemp -d)
./mvnw -B "-Dmaven.repo.local=$example_repo" -DskipTests -Dmaven.javadoc.skip=true install
cd examples/customer-profile-spring-boot
python3 scripts/verify.py --maven-repository "$example_repo"
```

Windows PowerShell（同样从 ent-loom 仓库根目录开始）：

```powershell
$repo = Join-Path $env:TEMP ("ent-loom-example-m2-" + [guid]::NewGuid().ToString("N"))
./mvnw.cmd "-Dmaven.repo.local=$repo" -DskipTests -Dmaven.javadoc.skip=true install
Set-Location examples/customer-profile-spring-boot
./scripts/verify.ps1 -MavenRepository $repo
```

隔离 Maven 仓库保留供排查或复用，用完后可删除该临时目录。CI 的“单表示例 MySQL 验收”作业执行同一路径，并上传验收日志；它验证当前工作区构件，不替代公开发布版本的消费验收，也不替代框架测试。

## 停止和清理

日常开发时停止应用即可；本机 MySQL 按本机服务管理方式停止。

```powershell
docker compose down
docker compose down -v
```

第二条命令会删除这个示例的 MySQL 数据卷。开发态主体、全量数据范围和日志审计配置只适合本地演示；生产项目应接入真实认证主体、权限服务、数据范围和审计存储，并使用正式数据库迁移工具替代 `schema.sql`。
