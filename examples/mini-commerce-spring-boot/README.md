# Mini Commerce Spring Boot 示例

通过商品、客户、下单、订单详情、商家批量改价和双端订单分页展示实体编程的业务闭环。所有能力随应用默认启用，无需选择 Profile。订单在事务中保存商品名称、成交单价和金额快照；商家报价演示身份、数据归属、字段权限和批量乐观锁；商家子订单演示同一实体按用户端、商家端开放不同数据范围。

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
| 商家报价治理 | [merchant.http](requests/merchant.http) | 两个商家读取隔离、批量改价、跨商家拒绝、旧版本整批回滚 |
| 双端订单分页 | [order-entries.http](requests/order-entries.http) | 用户本人范围、商家本店范围、额外筛选、稳定分页与总数、跨端凭证拒绝 |
| 实体文档 | [documentation.http](requests/documentation.http) | Meta 全部实体契约、独立主体授权 |

代码按 customer、product、order、merchant 业务分包，access 集中提供双端可信认证和身份核验。原商城调用方向为 `内置 Controller → Gateway → 统一治理与场景分发 → Handler / 查询引擎 → DAO`；商家报价与双端分页使用 `自定义 Controller → EntAccess → AuthorizedQueryExecutor / EntityDaoAuthorizationProvider → JDBC`。应用实现实体、DAO、必要的 DTO 和下单 Handler，配置集中声明框架能力与治理规则。

商品、客户使用 `/api/ent-crud/{entity}/*` 默认 CRUD。下单使用 `POST /api/ent-crud/order/action/place`，订单详情使用 `POST /api/ent-crud/order/detail`。详情通过 `options.resultMode: "ENTITY"` 保留实体字段名，通过 `expandRelations: ["customer", "orderItemList"]` 展开关联；不展开时只读取订单本身。明细始终使用下单快照。

## 配置与业务入口

### 先区分实体与归属者

`CustomerAddress` 是顾客的地址，`MerchantPoster` 是商户的文章。二者先演示只读归属查询，由主 YAML 声明 `view` 并复用内置授权查询；Meta 自动注册、DDL 自动建表、实体文档自动展示。

| 被访问的实体资源 | 当前访问身份 | 实体归属字段 | 最终数据范围 |
| --- | --- | --- | --- |
| `customerAddress`（顾客地址） | `customer` → `CustomerIdentity` | `customerId` | 只读当前顾客自己的地址 |
| `merchantPoster`（商户文章） | `merchant` → `MerchantIdentity` | `merchantId` | 只读当前商户自己的文章 |

看 [application.yml](src/main/resources/application.yml) 时，以所在位置判断名称：`entities.customerAddress` 是实体；`policies.customer-address.customer` 中的 `customer` 是身份；`relations.customer` 是归属关系角色，将实体 `customerId` 与可信身份 `customerId` 关联。商户文章同理。

已有 `Customer` 实体保存顾客资料，资源码也叫 `customer`；它与 `CustomerIdentity` 分工不同，身份的 `customerId` 对应资料的 `id`。当前没有 `Merchant` 主数据实体，`merchant` 身份由演示账号核验得到；有商户身份不要求必须有一个同名实体类。

这两个实体暂不开放通用 HTTP 路由，未增加创建、修改或发布流程；归属读取与跨身份拒绝由 [OwnedEntityIntegrationTest](src/test/java/com/example/minicommerce/access/OwnedEntityIntegrationTest.java) 使用真实主 YAML 和 JDBC 验证。文章正文使用 `TEXT`，其余字段复用默认类型。

### 全局归属约定，实体只声明例外

主配置集中声明：

```yaml
access:
  defaults:
    scope-fields: [merchantId, customerId]
  entities:
    order:
      exclude-scope-fields: [customerId]
controller:
  enabled: true
  include-entities: [product, customer, order]
```

候选字段只匹配实体已有的持久化字段。`CustomerAddress.customerId`、`MerchantPoster.merchantId`、`MerchantProduct.merchantId` 和 `MerchantOrder` 两个归属字段自动进入范围元数据，并禁止普通 Patch 修改。缺少候选字段的实体不会被补造字段，`Customer.id` 也不会变成归属字段。它们只需 `@EntEntity` 和业务字段，策略继续在主 YAML 中声明。

`Order.customerId` 是本示例原下单链的显式例外：该链尚未切换可信顾客归属写入，因此保留原开发演示行为。仅配置 `exclude-scope-fields` 不会为实体生成策略绑定。其他业务关联字段可在 YAML 排除，或用 `@EntCrudEntity(excludeScopeFields = "customerId")` 声明例外；未知实体/字段以及显式登记与排除冲突在启动时拒绝。

归属字段保护不授予权限，也不自动把全部候选字段拼成查询条件。顾客查地址按当前 `customerId`；商户查文章按当前 `merchantId`；双端查子订单分别按选中的身份关系约束。HTTP 复用默认白名单模式，新实体默认不开放通用路由。

### 可运行的商家批量改价

启动应用后，另一个终端在本目录执行初始化 SQL（也可用数据库客户端执行同一文件）：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/merchant-demo.sql
```

脚本只插入缺失数据，不重置价格或版本。新报价表不影响已有表结构；报价创建暂由初始化 SQL 完成。`Product` 是主数据，`MerchantProduct` 是商家报价，原下单流程仍使用主数据价格。

| 演示令牌 | 实际主体 | 当前商家 | 可读写报价 |
| --- | --- | --- | --- |
| `demo-merchant-a` | `merchant-user-a` | `1001` | `101`、`102` |
| `demo-merchant-b` | `merchant-user-b` | `2001` | `201` |

令牌与账号归属配置在 `application.yml` 的 `demo.merchant.accounts`。这些令牌只供本地演示，正式应用需接入实际登录。先读取报价，再以读取到的版本改价：

```bash
curl -H 'Authorization: Bearer demo-merchant-a' http://localhost:8082/api/merchant/products
curl -X POST http://localhost:8082/api/merchant/products/prices \
  -H 'Authorization: Bearer demo-merchant-a' -H 'Content-Type: application/json' \
  -d '{"items":[{"id":101,"price":11.50,"version":0},{"id":102,"price":22.00,"version":0}]}'
```

成功返回 `data: [101,102]`，对应版本均加一。按 [merchant.http](requests/merchant.http) 顺序执行，能验证跨商家和过期版本均返回 `409 / PRICE_UPDATE_REJECTED`，首项价格、版本也回滚。不存在记录使用相同响应，避免泄露其他商家数据；无凭证或无效凭证返回 403；通用报价 CRUD 返回 404。

具体配置和框架的分工：

- `DemoAccessConfiguration` 核验商家身份；主 YAML 的 `merchant-pricing` 策略声明 `view/update` 与字段方案，框架按 `merchant` 关系生成 `merchantId=当前商家`，`WRITE` 仅允许 `price`。
- `MerchantProduct.merchantId` 命中全局归属字段约定，禁止 Patch 修改归属；`productId` 只读。通用 HTTP 白名单只开放商品、客户和订单，商家报价由专用入口维护。
- `MerchantPricingController` 用 `@EntAccess` 要求商家身份及 HUMAN 主体；请求只绑定 `id/price/version`，单批 1～100 条，重复 ID 拒绝。
- `MerchantPricingService` 调用受授权查询和批量 DAO；JDBC 批量 UPDATE 先校验全部 Patch，再在同一事务内逐目标执行 `WHERE id=? AND merchant_id=? AND version=?`，任一目标影响行数非 1 时回滚。
- 框架拥有最终提交边界，禁止嵌套外层事务；提交后才记录成功审计。审计存储故障只写日志，不把已提交业务误报为失败；本轮未提供持久审计重试。

### 主 YAML 声明策略

[application.yml](src/main/resources/application.yml) 的 `ent.loom.crud.access` 直接生效，启动时编译为现有 Core 模型：

| 配置 | 表达的业务意图 | 框架处理 |
| --- | --- | --- |
| `policies` | 哪个身份能执行哪些动作 | 编译身份分支与动作上限 |
| `entities` | 实体选择策略、登记动作与 CRUD 映射 | 使用 Meta 资源码，不重复填写 Java 类名 |
| `relations` | 身份与实体的归属关系 | 按次读取可信身份属性，生成受约束 SQL |
| `field-plans` | 返回、写入、筛选、排序等字段用途 | 未声明用途不授权，并与实体静态上限取交集 |

例如双端子订单的策略：

```yaml
policies:
  order-entries:
    customer:
      actions: [view]
      fields: customer-orders
    merchant:
      actions: [view]
      fields: merchant-orders
entities:
  merchantOrder:
    policy: order-entries
    operations:
      view: [PAGE]
relations:
  customer:
    field: customerId
  merchant:
    field: merchantId
```

`scope` 缺省选择身份同名关系；关系的 `field`、`identity-field` 缺省为“关系名 + Id”。`fields` 可以省略，但必须已登记身份同名的安全字段方案。本例两端筛选能力不同，所以显式选择方案。实体可省略 `policy` 并选择已登记的同名策略；多个实体复用模板则显式指定。缺失关系、字段方案、未知实体/身份/动作/字段和重复声明均启动失败，不回退全量授权。

关系每项至少填写 `field` 或 `identity-field`，不使用空对象登记。Java 只保留 `DemoAccessConfiguration` 的认证和两种身份归属核验；两个业务策略配置类以及手写权限、简单关系解析 Bean 已删除。Controller 的 `@EntAccess` 继续约束可信入口和身份。复杂动态权限可提供 `BusinessPermissionEvaluator`，结果与 YAML 字段上限取交集，拒绝不会被配置重新放开；复杂关系继续通过 Java SPI 接入。

读取上限为 100 条，本轮批量存储实现支持 UPDATE；批量 DELETE 仍默认拒绝。

### 同一实体的用户端与商家端分页

两个端都使用 `page`，查询同一张 `merchant_order` 表。当前归属来自已认证身份，额外筛选与授权范围以 AND 合并；数据和 `total` 使用同一范围。

| 入口 | 固定授权范围 | 可选业务筛选 |
| --- | --- | --- |
| `POST /api/consumer/orders/page` | `customerId=当前客户` | `merchantId` |
| `POST /api/merchant/orders/page` | `merchantId=当前商家` | `customerId` |

启动应用后，在本目录准备演示子订单：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < scripts/order-entry-demo.sql
```

脚本只插入缺失记录。一个客户可跨商家购买，同一主订单可有多个商家子订单。本轮只验证分页治理，原下单流程尚未自动生成商家子订单。

| 演示令牌 | 当前身份 | 可见子订单 ID |
| --- | --- | --- |
| `demo-customer-a` | 客户 `301` | `501`、`502`、`504` |
| `demo-customer-b` | 客户 `302` | `503`、`505` |
| `demo-merchant-a` | 商家 `1001` | `501`、`503`、`504` |
| `demo-merchant-b` | 商家 `2001` | `502`、`505` |

账号分别配置在 `application.yml` 的 `demo.customer.accounts` 和 `demo.merchant.accounts`。例如：

```bash
# 用户 A 查询本人订单，并筛选商家 1001：501、504，total=2
curl -X POST http://localhost:8082/api/consumer/orders/page \
  -H 'Authorization: Bearer demo-customer-a' -H 'Content-Type: application/json' \
  -d '{"pageNumber":1,"pageSize":20,"merchantId":1001}'
# 商家 A 查询本店订单：501、503、504，total=3
curl -X POST http://localhost:8082/api/merchant/orders/page \
  -H 'Authorization: Bearer demo-merchant-a' -H 'Content-Type: application/json' \
  -d '{"pageNumber":1,"pageSize":20}'
```

响应包含 `data.items / total / pageNumber / pageSize`。页码从 1 开始，缺省单页 20 条、上限 100，固定按 `id ASC` 排序；参数非法返回 400，无效凭证、跨端令牌返回 403。按 [order-entries.http](requests/order-entries.http) 可继续验证额外筛选、第二页和伪造归属：用户提交 `customerId`、商家提交 `merchantId` 返回 `400 / VALIDATION_ERROR`；伪造入口 Header 也不能切换端。

配置与框架分工：

- `DemoAccessConfiguration` 将 Token 绑定主体与身份，由服务端路由确定入口；范围和字段权限来自主 YAML。
- 主 YAML 将两端身份分支绑定同一 `merchantOrder` 策略，`PAGE` 统一映射到 `view`。
- 两个 Controller 用 `@EntAccess` 声明当前身份和入口；`OrderPageService` 只构建分页和额外筛选，复用框架 `AuthorizedQueryExecutor.page`，不手写范围 SQL。
- `MerchantOrder.customerId/merchantId` 自动成为归属字段，具体数据范围由身份分支决定；实体未进入通用 HTTP 白名单，`POST /api/ent-crud/merchantOrder/page` 返回 404。

详细接入见[双端分页入口与范围](../../docs/guides/crud/双端分页入口与范围.md)。

[application.yml](src/main/resources/application.yml) 按运行基础、DDL 建表、实体元数据、CRUD 接口与写入契约、演示主体与访问治理、实体文档组织。数据库、端口通过环境变量覆盖；业务能力统一启用，导入导出沿用框架默认关闭。

原通用 CRUD 演示主体为 `local-developer`，允许维护商品，创建、更新和读取客户，以及读取订单和下单。客户删除及订单直接创建、修改、删除未授权；通用 HTTP 白名单为 `[product, customer, order]`，其余实体默认不暴露。文档复用 Meta 全量实体与非隐藏字段；文档访问不授予 CRUD 权限。两端自定义接口独立使用认证后的 `AccessSubject` 与 `MerchantIdentity / CustomerIdentity`。

默认业务入口为 `consumer`。商品读取可见性按「实体 → 入口 → 条件」配置：消费者只看 `active=true` 商品；不传筛选条件仍自动过滤，传 `active=false` 返回空页，停用商品详情返回 404。下单动作通过 `@EntCrudActions` 限定为消费者入口，Handler 另行校验商品启用状态、数量和客户。

管理端对照演示：停止应用，以相同数据库和管理入口重启，再执行 `visibility.http`。

```bash
../../mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--ent.loom.crud.governance.access-entry=management
```

管理入口允许读取所有商品状态，下单返回 403；未绑定入口（如 unknown）读取商品与下单均返回 403。入口由 Starter 从启动配置固定识别，HTTP 请求不能切换入口；请求伪造 `crudAccessEntry` 返回 400。

本地演示主体保留主数据写权限，便于准备场景数据。示例已实现双端商家子订单读取归属，不包含正式登录、租户、支付、库存和前端管理台；原主订单通用接口仍是开发演示入口。生产接入需替换演示主体与全量数据范围，根据已认证身份和受保护路由识别入口，并接入正式权限、数据库迁移与审计；消费者、商家、运营三端的角色、权限和数据范围由业务应用自行规划和实现。

全部配置与扩展方式见[配置参考](../../docs/guides/配置参考.md)、[默认装配与定制](../../docs/guides/默认装配与定制.md)、[读取可见性](../../docs/guides/读取可见性.md)。

### 三端、登录身份与数据范围如何配合

`consumer / merchant / management` 是业务入口名称，由应用约定；框架不会据此创建账号、登录接口或角色。原通用 CRUD 使用 `example.subject-id: local-developer` 和固定 `access-entry`。两端自定义接口使用上节的可信认证和身份注册，入口由服务端路由确定。原通用 CRUD 切换到 `management` 后仍使用原主体和 grants，只改变入口相关的可见性和动作准入。

完整商城可按下面的业务边界扩展；当前示例已实现商家报价隔离和双端子订单读取范围，尚未实现三端正式登录和自动拆单。

| 入口 | 登录后需确定的信息 | 典型操作权限 | 数据范围 |
| --- | --- | --- | --- |
| `consumer` 消费者 | 账号主体、绑定的客户 ID | 商品读取、本人资料维护、下单、订单读取 | 启用商品、本人资料和本人订单 |
| `merchant` 商家 | 员工主体、所属商家、已验证的店铺成员关系 | 商品维护、本店订单读取；发货等需另建业务动作 | 本店商品（含停用）、本店订单 |
| `management` 平台管理 | 员工主体、平台岗位及授权范围 | 按岗位授予商品管理、订单查询等权限 | 被授权的平台数据；进入管理端不代表拥有全部权限 |

一次请求需要分别确定和检查：

1. **登录认证：你是谁。** 两端演示通过 `CrudHttpAuthenticationSource` 验证 Token，再由 `BusinessIdentityRegistration` 校验主体的商家或客户归属并形成身份快照；旧通用 CRUD 保留 `CrudSubjectResolver / SubjectContext` 演示路径。不要把前端提交的归属 ID 当作已认证身份。
2. **业务入口：从哪一端办理业务。** 两端接口的可信入口由服务端 `/api/merchant/`、`/api/consumer/` 路由确定；原通用 CRUD 使用配置中的固定入口。正式应用按受保护路由和已认证上下文识别入口，不能信任客户端 Header 或请求参数。
3. **操作授权：能做什么。** 当前 `grants` 的键是主体 ID，不是入口或角色。新增 `merchant:` 键只会给名为 `merchant` 的主体授权。正式应用可通过 `CrudPermissionService` 接入账号、角色和权限关系，并结合入口判断；消费者不应获得本例用于准备数据的 `product:*`。通配 `*` 会匹配该资源的操作和场景，不仅限于标准 CRUD，仍须通过其他准入检查。
4. **数据范围：能操作谁的数据。** 商家报价和双端分页通过主 YAML 的策略和简单身份关系生成范围，由受授权查询和 DAO 落实到 SQL；双端分页的数据与总数同时受限。原通用 CRUD 的旧范围入口为 `CrudDataScopeResolver / EntityDaoScopeResolver`。正式应用创建时由服务端确定归属，更新时禁止任意迁移归属；只限制查询不能保证写入安全。
5. **读取可见性与业务动作：当前入口能看到、办理什么。** `read-visibility.product.consumer.active: true` 追加商品状态条件；`management: all` 仅表示不追加该状态条件，不能突破已授权的数据范围。`@EntCrudAction(accessEntry = "consumer")` 使 `place` 只对消费者入口开放，即使管理主体拥有 `order:place` 也不能从管理入口下单；动作内仍需校验业务规则。

商品主数据没有商家归属，商家报价已有 `merchantId` 并落实读写隔离；`MerchantOrder` 已有 `customerId / merchantId`，两端凭证绑定归属并落实读取范围。原 `Order` 仍是开发演示模型，下单尚未生成子订单。完整商城需继续接入真实登录与拆单规则。商家报价和子订单通过专用授权查询读取，不依赖 `read-visibility.product`；原主数据未绑定 `merchant` 可见性，仍不能从该入口直接读取商品。

生产接入时关闭 `ent.loom.crud.example.enabled`，实现上述认证、入口、权限和数据范围扩展。关闭演示模式本身不会自动生成登录和授权实现。`ent.loom.doc.contract.exposure.subject-ids` 是独立的文档访问白名单，更换演示主体也不会自动获得文档权限。

## 验证

在本目录使用 JDK 21 执行：

```bash
../../mvnw clean test
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify.py
# Windows: ./scripts/verify.ps1
```

集成测试在 H2 MySQL 模式验证默认配置、主体授权、消费者与管理入口、分页过滤与排序、下单校验、关联展开、价格快照，以及商家报价隔离、批量乐观锁和整批回滚。双端分页通过真实 HTTP 验证身份范围、额外筛选 AND、count、分页和越权拒绝；测试自身不包裹事务。Python 单元测试验证验收脚本的就绪等待、断言和资源清理。

MySQL 验收需要 Docker 和 Python 3.9+，使用独立 Compose 项目及临时端口，等待应用 readiness 和八张业务表就绪。覆盖文档契约、主数据创建、读取可见性、下单、详情、价格快照，以及商家读取隔离、整批提交、跨商家/不存在/旧版本/第二项 SQL 错误回滚和 SQL 核对；双端分页另核对固定范围、额外筛选、分页总数、伪造归属和跨端拒绝。不读取本机 `.env`。结束后停止应用并清理验收数据卷，日志保留在 `target/verification-logs/`。隔离仓库安装时传入 `--maven-repository <临时目录>`。

实体使用 `@EntEntity("订单")` 等简写。示例通过 `meta.base-packages` 限定业务实体包，CRUD、DDL 和 DOC 共用该来源；HTTP 复用默认 `EXPLICIT` 模式，只填写 `include-entities: [product, customer, order]`。资源码 `merchantProduct / merchantOrder` 对应表名 `merchant_product / merchant_order`，配置按资源码填写。其他应用未指定实体来源时，Meta 默认扫描 Boot 启动包。DDL 仅需配置 `mode: CREATE_TABLE`，不执行 DDL 时使用 `NONE`。查询、命令与 Meta 适配默认启用；文档保留主体白名单，未配置展示范围时展示全部 Meta 实体。对外契约可独立配置 `exposure.include-entities`、`exclude-entities` 和 `fields`。

示例通过 `ent.loom.meta.defaults.service: mini-commerce` 设置业务服务名，与应用名 `mini-commerce-spring-boot` 区分。单个实体可用 `@EntEntity(service = "其他服务")` 覆盖；未配置默认值和实体服务时使用 `spring.application.name`，应用名也未提供时才保持为空。`description` 仅在需要额外说明时填写。

实体与表名沿用框架默认命名：`Order → order`、`OrderItem → order_item`。示例通过 `ent.loom.meta.defaults.id-policy: DATABASE` 统一设置主键策略，无需在字段重复声明自增；手写 SQL 使用反引号引用关键字表名 `order`，框架生成的 SQL 自动引用标识符。字段默认非空，字符串长度与金额精度使用缺省配置，实体仅补充关联查询索引。
