# Mini Commerce Spring Boot 示例

通过商品、客户、下单和订单详情展示实体编程的完整业务闭环。所有能力随应用默认启用，无需选择 Profile。订单在事务中保存商品名称、成交单价和金额快照。

## 启动

开发与测试使用 JDK 21；MySQL 8+，Compose 使用 Docker Compose v2.20+。在本目录执行：

```bash
cp .env.example .env
docker compose up -d --wait mysql
../../mvnw spring-boot:run
```

Windows 使用 `Copy-Item .env.example .env` 和 `../../mvnw.cmd spring-boot:run`。

连接已有 MySQL 时，修改 `.env` 中的 `MYSQL_HOST`、`MYSQL_PORT`、数据库和账号，然后直接启动应用。默认应用端口为 8082、Compose 数据库端口为 3308。应用读取当前目录的 `.env`，启动时由 `ent-loom-ddl` 按实体注解自动建表，不清空已有数据；数据库需预先存在，账号需具有建表权限。停止 Compose 使用 `docker compose down`，加 `-v` 会删除数据卷。

表结构由 Meta 共享字段语义、`@EntDdlEntity` 和 `@EntDdlIndex` 定义。`ent.loom.meta.defaults.id-policy: DATABASE` 统一驱动 CRUD 的主键回填与 MySQL 整数主键自增；字段使用缺省类型：字符串 `varchar(200)`、金额 `decimal(20,6)`、枚举 `varchar(64)`，无需 `@EntDdlField`。采用 `CREATE_TABLE` 模式，仅创建缺失的表，不更新已有表结构。示例新建表不使用物理外键：下单校验客户、商品，并在同一事务内保存订单及明细；默认权限关闭客户删除和订单直接写入，明细不开放独立 HTTP 接口。绕过这些业务入口直接写库不受上述保障。已有库中的外键不会自动删除；生产结构变更使用版本化迁移。

使用当前工作区构件时，先在 ent-loom 仓库根目录执行：

```bash
./mvnw -pl :ent-loom-crud-spring-boot-starter,:ent-loom-meta-spring-boot-starter,:ent-loom-ddl-spring-boot-starter -am -DskipTests -Dmaven.javadoc.skip=true install
```

版本号相同不代表 Maven Central 已包含工作区能力。

## 演示场景

所有场景使用同一个应用与数据库。建议先运行业务闭环，再查看独立能力；请求中的主键变量按创建响应更新。

| 场景 | 请求文件 | 展示内容 |
| --- | --- | --- |
| 商城业务闭环 | [commerce.http](requests/commerce.http) | 创建商品与客户、下单、展开订单关联、订单直接写入拒绝、商品分页 |
| 通用 CRUD | [crud.http](requests/crud.http) | 客户创建、更新、分页、详情，以及删除拒绝 |
| 读取可见性 | [visibility.http](requests/visibility.http) | 自动过滤、反向条件约束、隐藏详情、入口伪造拒绝、管理端对照 |
| 实体文档 | [documentation.http](requests/documentation.http) | 商品与客户的实体契约、主体和字段白名单 |

代码按 customer、product、order 业务分包。调用方向为 `内置 Controller → Gateway → 统一治理与场景分发 → Handler / 查询引擎 → DAO`。应用实现实体、DAO、必要的 DTO 和下单 Handler，配置集中声明框架能力与治理规则。

商品、客户使用 `/api/ent-crud/{entity}/*` 默认 CRUD。下单使用 `POST /api/ent-crud/order/action/place`，订单详情使用 `POST /api/ent-crud/order/detail`。详情通过 `options.resultMode: "ENTITY"` 保留实体字段名，通过 `expandRelations: ["customer", "orderItemList"]` 展开关联；不展开时只读取订单本身。明细始终使用下单快照。

## 配置与业务入口

[application.yml](src/main/resources/application.yml) 按运行基础、DDL 建表、实体元数据、CRUD 接口与写入契约、演示主体与访问治理、实体文档组织。数据库、端口通过环境变量覆盖；业务能力统一启用，导入导出沿用框架默认关闭。

默认主体为 `local-developer`，允许维护商品，创建、更新和读取客户，以及读取订单和下单。客户删除及订单直接创建、修改、删除未授权；`OrderItem` 通过 HTTP 实体黑名单关闭独立接口，订单不进入公共文档白名单。

默认业务入口为 `consumer`。商品读取可见性按「实体 → 入口 → 条件」配置：消费者只看 `active=true` 商品；不传筛选条件仍自动过滤，传 `active=false` 返回空页，停用商品详情返回 404。下单动作通过 `@EntCrudActions` 限定为消费者入口，Handler 另行校验商品启用状态、数量和客户。

管理端对照演示：停止应用，以相同数据库和管理入口重启，再执行 `visibility.http`。

```bash
../../mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--ent.loom.crud.governance.access-entry=management
```

管理入口允许读取所有商品状态，下单返回 403；未绑定入口（如 unknown）读取商品与下单均返回 403。入口由 Starter 从启动配置固定识别，HTTP 请求不能切换入口；请求伪造 `crudAccessEntry` 返回 400。

本地演示主体保留主数据写权限，便于准备场景数据。示例不包含登录、租户、订单归属限制、支付、库存和前端管理台。生产接入需替换演示主体与全量数据范围，根据已认证身份和受保护路由识别入口，并接入正式权限、数据库迁移与审计。

全部配置与扩展方式见[配置参考](../../docs/guides/配置参考.md)、[默认装配与定制](../../docs/guides/默认装配与定制.md)、[读取可见性](../../docs/guides/读取可见性.md)。

## 验证

在本目录使用 JDK 21 执行：

```bash
../../mvnw clean test
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify.py
# Windows: ./scripts/verify.ps1
```

集成测试在 H2 MySQL 模式验证默认配置、主体授权、消费者与管理入口、分页过滤与排序、下单校验、关联展开、价格快照和事务回滚，测试自身不包裹事务。Python 单元测试验证验收脚本的资源清理。

MySQL 验收需要 Docker 和 Python 3.9+，使用独立 Compose 项目、临时端口和固定消费者入口，覆盖文档契约、主数据创建、默认读取可见性、下单、详情、价格快照、失败回滚和 SQL 核对；不读取本机 `.env`。结束后停止应用并清理验收数据卷，日志保留在 `target/verification-logs/`。隔离仓库安装时传入 `--maven-repository <临时目录>`。

实体使用 `@EntEntity("订单")` 等简写，统一通过 `ent.loom.meta.defaults.service: mini-commerce` 设置所属服务。单个实体可用 `@EntEntity(service = "其他服务")` 覆盖；未配置默认值和实体服务时保持为空。`description` 仅在需要额外说明时填写。

实体与表名沿用框架默认命名：`Order → order`、`OrderItem → order_item`。示例通过 `ent.loom.meta.defaults.id-policy: DATABASE` 统一设置主键策略，无需在字段重复声明自增；手写 SQL 使用反引号引用关键字表名 `order`，框架生成的 SQL 自动引用标识符。字段默认非空，字符串长度与金额精度使用缺省配置，实体仅补充关联查询索引。
