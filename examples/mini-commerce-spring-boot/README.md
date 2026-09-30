# Mini Commerce Spring Boot 示例

通过商品、客户、下单和订单详情展示实体编程的最小业务闭环。完整商城可在业务应用层扩展消费者、商家、运营三端；本示例只演示消费者入口及管理侧读取对照，不是三端后台模板。所有能力随应用默认启用，无需选择 Profile。订单在事务中保存商品名称、成交单价和金额快照。

## 启动

开发与测试使用 JDK 21；MySQL 8+，Compose 使用 Docker Compose v2.20+。在本目录执行：

```bash
cp .env.example .env
docker compose up -d --wait mysql
../../mvnw spring-boot:run
```

Windows 使用 `Copy-Item .env.example .env` 和 `../../mvnw.cmd spring-boot:run`。

连接已有 MySQL 时，修改 `.env` 中的 `MYSQL_HOST`、`MYSQL_PORT`、数据库和账号，然后直接启动应用。默认应用端口为 8082、Compose 数据库端口为 3308。应用读取当前目录的 `.env`，启动时由 `ent-loom-ddl` 按实体注解自动建表，不清空已有数据；数据库需预先存在，账号需具有建表权限。停止 Compose 使用 `docker compose down`，加 `-v` 会删除数据卷。

表结构由 `@EntEntity`、Meta 共享字段语义和 `@EntDdlIndex` 定义。DDL 默认复用 Meta 扫描的实体，无需重复配置 `ddl.base-packages`；仅需 DDL 专属配置或覆盖时添加 `@EntDdlEntity(...)`，省略空注解。`ent.loom.meta.defaults.id-policy: DATABASE` 统一驱动 CRUD 的主键回填与 MySQL 整数主键自增；字段使用缺省类型：字符串 `varchar(200)`、金额 `decimal(20,6)`、枚举 `varchar(64)`，无需 `@EntDdlField`。采用 `CREATE_TABLE` 模式，仅创建缺失的表，不更新已有表结构。示例新建表不使用物理外键：下单校验客户、商品，并在同一事务内保存订单及明细；默认权限关闭客户删除和订单直接写入，明细不开放独立 HTTP 接口。绕过这些业务入口直接写库不受上述保障。已有库中的外键不会自动删除；生产结构变更使用版本化迁移。

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
| 实体文档 | [documentation.http](requests/documentation.http) | Meta 全部实体契约、独立主体授权 |

代码按 customer、product、order 业务分包。调用方向为 `内置 Controller → Gateway → 统一治理与场景分发 → Handler / 查询引擎 → DAO`。应用实现实体、DAO、必要的 DTO 和下单 Handler，配置集中声明框架能力与治理规则。

商品、客户使用 `/api/ent-crud/{entity}/*` 默认 CRUD。下单使用 `POST /api/ent-crud/order/action/place`，订单详情使用 `POST /api/ent-crud/order/detail`。详情通过 `options.resultMode: "ENTITY"` 保留实体字段名，通过 `expandRelations: ["customer", "orderItemList"]` 展开关联；不展开时只读取订单本身。明细始终使用下单快照。

## 配置与业务入口

[application.yml](src/main/resources/application.yml) 按运行基础、DDL 建表、实体元数据、CRUD 接口与写入契约、演示主体与访问治理、实体文档组织。数据库、端口通过环境变量覆盖；业务能力统一启用，导入导出沿用框架默认关闭。

默认主体为 `local-developer`，允许维护商品，创建、更新和读取客户，以及读取订单和下单。客户删除及订单直接创建、修改、删除未授权；`OrderItem` 未列入 HTTP 包含清单，不开放独立接口。文档复用 Meta 全量实体与非隐藏字段，包括订单和订单明细；文档访问不授予 CRUD 权限。

默认业务入口为 `consumer`。商品读取可见性按「实体 → 入口 → 条件」配置：消费者只看 `active=true` 商品；不传筛选条件仍自动过滤，传 `active=false` 返回空页，停用商品详情返回 404。下单动作通过 `@EntCrudActions` 限定为消费者入口，Handler 另行校验商品启用状态、数量和客户。

管理端对照演示：停止应用，以相同数据库和管理入口重启，再执行 `visibility.http`。

```bash
../../mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--ent.loom.crud.governance.access-entry=management
```

管理入口允许读取所有商品状态，下单返回 403；未绑定入口（如 unknown）读取商品与下单均返回 403。入口由 Starter 从启动配置固定识别，HTTP 请求不能切换入口；请求伪造 `crudAccessEntry` 返回 400。

本地演示主体保留主数据写权限，便于准备场景数据。示例不包含登录、租户、订单归属限制、支付、库存和前端管理台。生产接入需替换演示主体与全量数据范围，根据已认证身份和受保护路由识别入口，并接入正式权限、数据库迁移与审计；消费者、商家、运营三端的角色、权限和数据范围由业务应用自行规划和实现。

全部配置与扩展方式见[配置参考](../../docs/guides/配置参考.md)、[默认装配与定制](../../docs/guides/默认装配与定制.md)、[读取可见性](../../docs/guides/读取可见性.md)。

### 三端、登录身份与数据范围如何配合

`consumer / merchant / management` 是业务入口名称，由应用约定；框架不会据此创建账号、登录接口或角色。当前配置中的 `example.subject-id: local-developer` 对所有请求生效，`access-entry: consumer` 对整个应用生效，两者互不替代。切换到 `management` 后仍是同一个主体、同一份 grants，只改变入口相关的可见性和动作准入。

完整商城可按下面的业务边界接入；表中为目标设计，当前示例尚未实现三端登录和数据归属隔离。

| 入口 | 登录后需确定的信息 | 典型操作权限 | 数据范围 |
| --- | --- | --- | --- |
| `consumer` 消费者 | 账号主体、绑定的客户 ID | 商品读取、本人资料维护、下单、订单读取 | 启用商品、本人资料和本人订单 |
| `merchant` 商家 | 员工主体、所属商家、已验证的店铺成员关系 | 商品维护、本店订单读取；发货等需另建业务动作 | 本店商品（含停用）、本店订单 |
| `management` 平台管理 | 员工主体、平台岗位及授权范围 | 按岗位授予商品管理、订单查询等权限 | 被授权的平台数据；进入管理端不代表拥有全部权限 |

一次请求需要分别确定和检查：

1. **登录认证：你是谁。** 业务应用验证 Session 或 Token，再通过 `CrudSubjectResolver` 映射为 `SubjectContext`。框架现有字段为 `subjectId / tenantId / orgId`；客户、商家、店铺关系由业务服务维护，不要把前端提交的归属 ID 直接当作已认证身份。同一账号可以拥有多个身份，但必须验证当前身份及店铺成员关系。
2. **业务入口：从哪一端办理业务。** 三端独立部署时，各应用可使用固定 `access-entry`，同时校验该端登录资格。同一应用承载三端时，替换 `AccessEntryResolver`，从服务端受保护路由和已认证上下文解析并校验入口；不能仅信任客户端 Header 或请求参数。`/consumer/**`、`/merchant/**`、`/management/**` 可作为应用路由设计，当前示例并未生成这些路由。
3. **操作授权：能做什么。** 当前 `grants` 的键是主体 ID，不是入口或角色。新增 `merchant:` 键只会给名为 `merchant` 的主体授权。正式应用可通过 `CrudPermissionService` 接入账号、角色和权限关系，并结合入口判断；消费者不应获得本例用于准备数据的 `product:*`。通配 `*` 会匹配该资源的操作和场景，不仅限于标准 CRUD，仍须通过其他准入检查。
4. **数据范围：能操作谁的数据。** 使用 `CrudDataScopeResolver` 限制 Gateway 数据范围；Handler 直接使用 DAO 时，还需通过 `EntityDaoScopeResolver` 落实相应范围。消费者订单按已认证账号绑定的 `customerId` 限制；商家商品和订单按店铺归属限制。创建时由服务端确定归属，更新时禁止任意迁移归属，下单时校验客户属于当前主体。只限制查询不能保证写入安全。
5. **读取可见性与业务动作：当前入口能看到、办理什么。** `read-visibility.product.consumer.active: true` 追加商品状态条件；`management: all` 仅表示不追加该状态条件，不能突破已授权的数据范围。`@EntCrudAction(accessEntry = "consumer")` 使 `place` 只对消费者入口开放，即使管理主体拥有 `order:place` 也不能从管理入口下单；动作内仍需校验业务规则。

当前商品没有商家或店铺归属字段，订单只有 `customerId`，尚未绑定登录账号。因此不能仅靠补充三份 YAML 获得三端隔离。接入商家前应补齐商品和订单归属模型，并处理跨店订单的拆分或明细归属；之后才适合增加 `merchant: all`，让商家在自己的数据范围内读取启用和停用商品。当前未绑定 `merchant`，该入口读取商品会返回 403。

生产接入时关闭 `ent.loom.crud.example.enabled`，实现上述认证、入口、权限和数据范围扩展。关闭演示模式本身不会自动生成登录和授权实现。`ent.loom.doc.contract.exposure.subject-ids` 是独立的文档访问白名单，更换演示主体也不会自动获得文档权限。

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

实体使用 `@EntEntity("订单")` 等简写。示例通过 `meta.base-packages` 限定业务实体包，CRUD、DDL 和 DOC 共用该来源；HTTP 使用 `ALL_REGISTERED` 模式，默认开放全部已注册实体，仅排除订单明细，新增实体会自动获得路由。其他应用未指定实体来源时，Meta 仍默认扫描 Boot 启动包。DDL 仅需配置 `mode: CREATE_TABLE`，不执行 DDL 时使用 `NONE`。查询、命令与 Meta 适配默认启用，无需重复开启；文档保留主体白名单，未配置展示范围时展示全部 Meta 实体。对外契约可独立配置 `exposure.include-entities`、`exclude-entities` 和 `fields`。

示例通过 `ent.loom.meta.defaults.service: mini-commerce` 设置业务服务名，与应用名 `mini-commerce-spring-boot` 区分。单个实体可用 `@EntEntity(service = "其他服务")` 覆盖；未配置默认值和实体服务时使用 `spring.application.name`，应用名也未提供时才保持为空。`description` 仅在需要额外说明时填写。

实体与表名沿用框架默认命名：`Order → order`、`OrderItem → order_item`。示例通过 `ent.loom.meta.defaults.id-policy: DATABASE` 统一设置主键策略，无需在字段重复声明自增；手写 SQL 使用反引号引用关键字表名 `order`，框架生成的 SQL 自动引用标识符。字段默认非空，字符串长度与金额精度使用缺省配置，实体仅补充关联查询索引。
