# Java 策略与统一绑定

普通实体优先通过 `@EntCrudGrant` 就地声明身份授权。需要复用关系角色模板的实体仍可使用 `@EntCrudEntity.policy` 选择策略，由 Java 的 `EntityPolicyTemplate` 与 `BusinessPermissionEvaluator` Bean 表达，使用同一个 `EntityPolicyRegistry`、`BusinessAuthorizationService` 和受授权执行器。策略码重名直接失败，不按来源设置覆盖优先级。

```java
@EntCrudEntity
@EntCrudGrant(identities = "operator", actions = "view", scope = EntCrudGrantScope.ALL)
class Order { /* 实体字段 */ }
```

## 实体授权注解（推荐入口）

`@EntCrudGrant` 属于 `ent-loom-crud-annotations`，可在实体上重复声明。`@EntCrudEntity` 限制实体能力，`@EntCrudGrant` 决定哪些身份可使用这些能力，两者不能互相替代。

```java
@EntCrudEntity(actions = {"view", "update"})
@EntCrudGrant(identities = "merchant", actions = {"view", "update"})
@EntCrudGrant(identities = "public", actions = "view", scope = EntCrudGrantScope.ALL, where = "active = true",
    fields = @EntCrudGrant.Fields(use = EntCrudFieldUse.RETURN, value = {"id", "name"}))
class Product { /* 实体字段 */ }
```

项目可统一声明归属字段与管理员全局 `*` 权限：

```yaml
ent:
  loom:
    crud:
      grant-defaults:
        owner-fields:
          merchant: merchantId
          customer: customerId
        wildcard-identities: [super-admin]
```

- `DEFAULT` 使用已注册身份的默认归属字段；主体实体可通过 `scopeField = "id"` 显式覆盖。普通身份仍须由实体显式授予操作资格。
- `grant-defaults.wildcard-identities` 是全局 `*` 身份集合：对使用声明式授权的 CRUD 实体，自动展开实体 `actions`，包括自定义动作，范围为全量、字段受实体上限约束；无需逐实体重复声明管理员。
- 实体上只要显式声明同一身份，就整体替换该身份的全局默认，不补回其他操作。`actions = {}` 显式禁止访问；例如订单声明管理员仅能 `view/cancel`，不继承下单资格。
- 只有 `@EntCrudEntity`、未绑定 Java 模板的实体也可使用全局授权；没有 CRUD 声明的实体不自动纳入，通用 HTTP 的关闭标记保持有效。显式选择 Java 模板的实体继续由其模板声明资格，不混入声明式默认。
- `ALL` 显式全量基础范围；匿名分支 `public` 必须显式声明 ALL，不借用登录身份权限。
- `where` 是开发者维护的静态 SQL 行条件，仅支持 `actions = "view"`；使用当前实体表的数据库列名，不写 WHERE 关键字或表别名，不拼接外部输入、不支持动态参数。框架不解析或转换 SQL，SQL 正确性由项目集成测试验证。
- `where` 始终收窄基础范围，默认与身份归属取交集；匿名与顾客跨归属查看公开商品时，显式声明 `scope = ALL`。租户等强制治理条件、动态扩展的收窄条件始终取交集。
- JDBC 统一将 `where` 整体加括号，用于列表、详情、计数等受授权读取；写入执行链拒绝 SQL 查看条件。
- 不声明 `fields` 时沿用实体字段上限；白名单仅开放列出字段，`exclude` 从实体允许集合中排除。`use` 缺省对全部用途统一收窄，显式 `use` 仅影响选中的用途，未选用途沿用实体上限；显式空 `value` 关闭选中用途。
- EXPAND 仅支持 `关系.字段` 投影，不能授权完整关联对象。
- 未注册身份、未知范围字段、字段越界、超出动作上限、重复身份/动作或重复的用途专属声明均启动失败。同一实体不能混用授权注解与模板/程序化绑定。

框架自动生成统一操作资格并提供默认判定器，项目无需编写资源白名单、分发 switch 或全局 `BusinessPermissionEvaluator`。动态需求使用按实体类型绑定的 `EntityGrantCustomizer` Bean：`getEntityType()` 指定实体，`customize(visitor, operation, targetId, declared)` 按次返回决策；返回 null 或拒绝即关闭权限，允许结果与静态授权的范围和字段取交集，不能扩大权限。重复扩展或未声明注解的目标实体启动失败。

订单状态、余额等动作执行约束继续留在业务服务和事务中，不编码成注解表达式。

## Java 只读策略（关系角色与程序化接入）

Java 模板声明身份分支和业务动作。例如，一个仅包含 `view` 的模板：

```java
@Bean
EntityPolicyTemplate orderRead() {
    return new EntityPolicyTemplate("order-read", "Project#orderRead", List.of(
        new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), List.of())));
}
```

有实体注解时，框架按 `actions` 自动登记操作并生成标准 CRUD 映射，无需逐个注册 `BusinessOperationDeclaration` 或 `CrudOperationMapping`。实体缺省动作集由 `ent.loom.crud.defaults.actions` 定义，未配置时仅 `view`。`addActions/removeActions` 在默认集上增删；显式 `actions` 完整替换且不能与增删混用，`actions = {}` 关闭全部动作；单条和批量写入必须分别显式开放。`operations = "create="` 关闭通用创建，并允许绑定同名业务 Handler。

对于程序化模板，`BusinessPermissionEvaluator` 是应用级业务判定器；它不覆盖 `EntCrudGrant` 实体的声明式授权：按可信访问者、资源、操作和目标返回允许/拒绝、数据范围及字段能力。没有判定器或返回空结果时默认拒绝。授权结果受实体字段静态上限约束，未列出的用途关闭；判定器可返回 `rule.getFieldLimit()` 表示沿用完整静态上限，也可显式收窄。关联 `EXPAND` 必须明确返回授权字段，不再由配置编译器自动开放。

需要关系事实时使用 `RelationFactResolver`；也可直接在判定器中根据可信身份返回类型明确的等值、集合或交集范围。空范围不能解释为全量。

实体选择 Java 模板后，不再额外注册同一实体的 `EntityPolicyBinding`；纯 Java 绑定也可使用实体注解声明字段与动作上限，两处字段上限取交集。超出实体动作上限、策略重名或绑定冲突均启动失败。

未使用实体注解的内部 Java 接入仍可显式登记业务操作、映射及策略绑定。MVC 原始查询通过 [BusinessQueryExecutor](MVC业务查询治理.md) 复用同一个 `BusinessAuthorizationService`，由框架统一处理授权拒绝、执行阶段和审计。业务准备查询参数并声明字段用途，原始 Mapper 必须实际落实授权范围与投影，不能只做一次允许检查。

`public` 表示不要求特定业务身份分支，HTTP 是否要求凭证仍由可信准入合同决定；它不代表绕过认证。身份相关业务应使用已注册身份分支，关系事实只取自可信身份。

可运行参考：

- [AccessAuthorizedQueryIntegrationTest](../../../ent-loom-modules/ent-loom-crud/ent-loom-crud-spring-boot-starter/src/test/java/com/entloom/crud/starter/web/access/AccessAuthorizedQueryIntegrationTest.java)：选择 Java 模板，经过真实 MVC、Gateway、授权与 JDBC，验证范围过滤、字段投影、分页计数、动态拒绝和审计；缺少动态判定器时拒绝读取。
- [EntityPolicySelectionTest](../../../ent-loom-modules/ent-loom-crud/ent-loom-crud-spring-boot-starter/src/test/java/com/entloom/crud/starter/config/EntityPolicySelectionTest.java)：HTTP 实体选择 Java 只读策略，验证写入不能退回旧治理、未知策略、已移除的 YAML 授权配置与 Java 策略重名与策略编译组件缺失。

## HTTP 与创建

关闭通用 HTTP 的内部实体无需访问方式占位：

```java
@EntCrudEntity(httpExposed = false)
```

关闭 HTTP 不授予 Java 调用权限，也不关闭应用自定义控制器。需要内部按次授权时仍可声明 `EntCrudGrant` 或选择复用策略，并使用相应授权入口。

单条 CREATE、单目标 UPDATE/DELETE 与显式批量 UPDATE 均使用策略授权。CREATE 在允许决策后校验请求必填，补齐服务端默认值并落实归属范围，再校验完整实体并在事务中写入；支持显式主键和数据库生成主键回填。创建归属范围仅接受 ALL、等值、IN 与 AND，不可确定的 NONE/OR 范围直接拒绝。CREATE 的试运行与幂等键，以及 CREATE_BATCH、DELETE_BATCH、SAVE_OR_UPDATE 等未接入的形状继续拒绝，不回退到另一条治理链。

客户示例已迁移到实体 `@EntCrudGrant`：mini-commerce 使用商城默认归属约定，令牌为 `demo-platform-admin`；customer-profile 直接声明 operator 全量范围，令牌为 `demo-profile-admin`，均无需项目全局判定器。

## 关系元数据与授权选择

结构关系只在字段上声明一次：

```java
@EntRelation(Merchant.class)
private Long sellerRef;
```

Java 策略显式选择目标实体，真实外键从统一注册表取得：

```java
var reference = entities.requireReference(Order.class, Merchant.class);
return BusinessPermissionDecision.allow(
    AuthorizationScope.equal(reference.getFromField(), trustedMerchantId), fields);
```

`trustedMerchantId` 必须来自可信身份。这里实际使用 `sellerRef`，与身份码和 ID 属性命名无关。同一目标有多条引用时，使用 `requireReference(Order.class, Merchant.class, "sellerRef")` 明确选择；框架不猜测。仅支持本地、类型匹配、指向目标主键的单值引用，自然键、集合及远程关系不能直接作为主体 ID 范围。

模板的 `requiredRelations` 也可直接选择关系属性，由统一元数据解析为实际字段，无须在实体上重复声明角色到外键的映射；关系事实仍由可信 `RelationFactResolver` 提供。模板声明本身不授予访问权限。

写入保护与结构关系独立：需要禁止普通载荷修改归属时，使用 `@EntCrudEntity(scopeFields = "sellerRef")`。普通 `@EntRelation` 保持普通字段能力，不因有关联就自动变成安全范围。创建时归属仍须由可信授权范围提供并校验。

身份配置使用 `ent.loom.crud.identities.<code>`，仅描述身份快照的 ID 属性和类型；不参与实体字段、关系或归属推导。

## 项目默认动作与实体差异

```yaml
ent:
  loom:
    crud:
      defaults:
        actions: [view, create, update, delete]
```

```java
@EntCrudEntity // 使用项目默认动作集
class Merchant { }

@EntCrudEntity(removeActions = "delete")
class Customer { }

@EntCrudEntity(addActions = "batch-update")
class Product { }

@EntCrudEntity(actions = {"view", "create", "cancel"}, operations = "create=")
class Order { }
```

同一动作不能同时增加和删除，追加已存在动作、删除不存在动作、动作重复以及替换和增删混用均启动失败。注解内部用保留标记区分未填写与显式空数组，业务代码无需使用该标记。解析后的动作集统一用于资格校验、全局星号授权、场景及 CRUD 映射；它不自动授予普通身份权限。自定义动作仍需 Handler，批量动作仍需对应执行能力。

## 默认归属保护字段

框架复用 `CrudGrantDefaults` 的归属字段值（如 `merchantId/customerId`），与当前实体的非主键持久化标量字段取交集，统一编译到 CRUD 运行时元数据。Meta 适配链与 Core 原生解析链使用同一解析器；查询授权、输入校验、字段上限和 DAO 写入消费同一标记，不需要另配 YAML 字段列表。

```java
@EntCrudEntity // 实际存在 merchantId/customerId 时自动保护
class CommerceOrder { }

@EntCrudEntity(scopeFields = "orderId", httpExposed = false)
class CommerceOrderItem { }

@EntCrudEntity(scopeFields = {}) // 关闭默认推导，字段自身显式保护仍有效
class SharedRecord { }

@EntCrudEntity(scopeFields = "sellerRef") // 完整替换默认字段
class Invoice { }
```

显式字段重复、未知或属于主键、关联对象时均启动报错。未配置全局归属约定时不推导任何默认保护字段；没有 CRUD 声明的实体也不自动加入。显式配置完整替换默认集合，项目默认归属字段变化时不会自动加入。

默认保护只约束写入，不授予操作资格。创建时归属由可信授权范围补齐或校验，普通更新不得转移归属。某身份已获操作资格却缺少其所需归属字段时，授权编译仍启动失败，不能用字段交集忽略错误。

## 简洁的统一字段规则

```java
@EntCrudGrant.Fields(exclude = {"secret", "bindingToken"}) // 所有用途统一排除
@EntCrudGrant.Fields(value = {"id", "name"}) // 所有用途统一白名单
@EntCrudGrant.Fields(use = {RETURN, FILTER, SORT}, value = {"id", "name"}) // 选择多个用途
@EntCrudGrant.Fields(use = FILTER, exclude = "totalAmount") // 特定用途例外
```

`use` 缺省为 `EntCrudFieldUse.ALL`。统一白名单分别与每个用途的实体上限取交集，不会让主键可写，也不会开放完整关联对象。只有显式选中的字段规则影响权限；没有声明 `fields` 时沿用实体字段上限。

一条 ALL 规则可与用途专属规则共同声明，两者按交集收窄，与声明顺序无关，专属规则不能重新放开已被统一规则排除的字段。ALL 不能与其他用途混写；空用途数组、重复 ALL 或重叠的专属用途声明均启动报错。固定用途仍严格校验字段是否在该用途上限内；ALL 按各用途分别取交集。
