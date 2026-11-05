# Java 策略与统一绑定

实体使用 `crud.access.policy` 选择策略，策略可以来自应用 YAML，也可以来自 Java 的 `EntityPolicyTemplate` Bean。两者使用同一个 `EntityPolicyRegistry`、`BusinessAuthorizationService` 和受授权执行器。策略码重名直接失败，不按来源设置覆盖优先级。

```yaml
# src/main/resources/ent/entities/order.yml
crud:
  access:
    policy: order-read
```

## Java 只读策略

Java 模板声明身份分支和业务动作。例如，一个仅包含 `view` 的模板：

```java
@Bean
EntityPolicyTemplate orderRead() {
    return new EntityPolicyTemplate("order-read", "Project#orderRead", List.of(
        new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(), List.of())));
}
```

还需要注册以下现有扩展点，不新增另一套策略接口：

| 扩展点 | 职责 |
|---|---|
| `BusinessOperationDeclaration` | 登记该实体的 `view` 业务操作 |
| `CrudOperationMapping` | 将允许的查询操作，例如 LIST、PAGE，映射到 `view`；未映射的操作拒绝执行 |
| `BusinessPermissionEvaluator` | 按可信访问者、操作和目标决定允许或拒绝；允许时明确返回数据范围和字段能力 |
| `RelationFactResolver` | 模板使用关系角色时提供可信关系事实；不使用关系角色则无需注册 |

实体文件负责选择模板，不再额外注册同一实体的 `EntityPolicyBinding`，否则按重复绑定拒绝启动。当前 Java 模板的操作目录、映射和字段授权由项目声明，不在实体文件混配 `operations` 或 `fields`。

Java 模板仅声明静态边界，不单独授予访问权限；没有 `BusinessPermissionEvaluator` 时默认拒绝。判定器返回的字段能力和范围仍受模板及实体元数据约束，不能用允许结果开放未映射的写入操作。`BusinessPermissionEvaluator` 是应用级扩展：与 YAML 策略并存时也会收窄 YAML 授权，项目应统一处理所有接入资源，不能只处理新增 Java 资源而误拒绝其它资源。

`public` 表示不要求特定业务身份分支，HTTP 是否要求凭证仍由可信准入合同决定；它不代表绕过认证。身份相关业务应使用已注册身份分支，关系事实只取自可信身份。

可运行参考：

- [AccessAuthorizedQueryIntegrationTest](../../../ent-loom-modules/ent-loom-crud/ent-loom-crud-spring-boot-starter/src/test/java/com/entloom/crud/starter/web/access/AccessAuthorizedQueryIntegrationTest.java)：实体文件选择 Java 模板，经过真实 MVC、Gateway、授权与 JDBC，验证范围过滤、字段投影、分页计数、动态拒绝和审计；缺少动态判定器时拒绝读取。
- [EntityPolicySelectionTest](../../../ent-loom-modules/ent-loom-crud/ent-loom-crud-spring-boot-starter/src/test/java/com/entloom/crud/starter/config/EntityPolicySelectionTest.java)：HTTP 实体选择 Java 只读策略，验证写入不能退回旧治理、未知策略、YAML/Java 重名与策略编译组件缺失。

## HTTP 与创建

关闭通用 HTTP 的内部实体无需访问方式占位：

```yaml
crud:
  http-exposed: false
```

关闭 HTTP 不授予 Java 调用权限，也不关闭应用自定义控制器。需要内部按次授权时仍可声明 `policy`，并使用相应授权入口。

单条 CREATE、单目标 UPDATE/DELETE 与显式批量 UPDATE 均使用策略授权。CREATE 在允许决策后校验请求必填，补齐服务端默认值并落实归属范围，再校验完整实体并在事务中写入；支持显式主键和数据库生成主键回填。创建归属范围仅接受 ALL、等值、IN 与 AND，不可确定的 NONE/OR 范围直接拒绝。CREATE 的试运行与幂等键，以及 CREATE_BATCH、DELETE_BATCH、SAVE_OR_UPDATE 等未接入的形状继续拒绝，不回退到另一条治理链。

客户示例已通过具名策略绑定可信管理身份：mini-commerce 使用 maintainable 的实体差异与全局管理员授权，令牌为 `demo-platform-admin`，customer-profile 使用 `profile-management` 和令牌 `demo-profile-admin`。实体通过 `crud.access.policy` 选择策略，不存在另一种访问模式。
