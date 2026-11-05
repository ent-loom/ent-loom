package com.entloom.crud.starter.config;

import com.entloom.declaration.DeclarationNode;
import com.entloom.declaration.EntityDeclarations;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CRUD 自行解释实体节点，产物继续交给既有策略、归属与输入契约编译器。 */
public final class CrudEntityDeclarations implements com.entloom.declaration.EntityDeclarationComponent {
    @Override public String componentCode() { return "crud"; }

    @lombok.Data
    public static class EntityOptions {
        private List<String> ownership;
        private Map<String, List<String>> operations;
        private Map<String, String> relations = new LinkedHashMap<>();
    }
    public enum FieldMode {
        INCLUDE, // 仅包含指定字段
        EXCLUDE, // 排除指定字段
        ALL, // 全部候选字段
        NONE // 关闭该用途
    }
    public record FieldPlan(FieldMode mode, List<String> fields, String source) { }
    public enum AccessMode {
        JAVA // 显式复用应用的 Java 治理链，不根据文件缺失隐式回退
    }
    private final Map<String, Set<String>> actions = new LinkedHashMap<>();
    private final Map<String, Set<com.entloom.crud.api.enums.CrudOperationKey>> allowedOperations = new LinkedHashMap<>();
    private final Map<String, Boolean> httpExposure = new LinkedHashMap<>();
    private final List<com.entloom.crud.core.governance.policy.ScenePolicy> scenes = new java.util.ArrayList<>();
    private final Map<String, AccessMode> accessModes = new LinkedHashMap<>();
    private final EntityDeclarations declarations;
    private final Map<String, EntityOptions> options = new LinkedHashMap<>();
    private final Map<String, String> policies = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<AuthorizationFields.Use, FieldPlan>>> fields = new LinkedHashMap<>();
    private final Map<String, List<String>> required = new LinkedHashMap<>();
    private final Map<String, List<String>> forbidden = new LinkedHashMap<>();

    public CrudEntityDeclarations(EntityDeclarations declarations) {
        this.declarations = declarations;
        declarations.entities().forEach((resource, root) -> {
            DeclarationNode crud = root.members().get("crud");
            if (crud == null) return;
            crud.only("access", "policy", "operations", "relations", "ownership", "fields", "contracts", "actions", "allowed-operations", "http-exposed");
            Map<String, DeclarationNode> nodes = crud.members();
            if (nodes.containsKey("http-exposed")) httpExposure.put(resource, nodes.get("http-exposed").bool());
            if (nodes.containsKey("actions")) parseActions(resource, nodes.get("actions"));
            if (nodes.containsKey("allowed-operations")) {
                Set<com.entloom.crud.api.enums.CrudOperationKey> keys = new java.util.LinkedHashSet<>();
                for (String name : nodes.get("allowed-operations").strings()) {
                    var query = com.entloom.crud.api.enums.QueryOperation.from(name);
                    var command = com.entloom.crud.api.enums.CommandOperation.from(name);
                    if (query == null && (command == null || command == com.entloom.crud.api.enums.CommandOperation.ACTION)) {
                        throw nodes.get("allowed-operations").error("未知标准操作: " + name);
                    }
                    keys.add(query != null ? com.entloom.crud.api.enums.CrudOperationKey.of(query) : com.entloom.crud.api.enums.CrudOperationKey.of(command));
                }
                allowedOperations.put(resource, Set.copyOf(keys));
            }
            if (nodes.containsKey("access")) {
                try { accessModes.put(resource, AccessMode.valueOf(nodes.get("access").text())); }
                catch (IllegalArgumentException exception) { throw nodes.get("access").error("未知访问模式，仅支持 JAVA；声明式授权使用 policy"); }
                if (nodes.containsKey("policy") || nodes.containsKey("fields") || nodes.containsKey("operations")) {
                    throw crud.error("JAVA 访问模式不得同时声明 policy、fields 或 operations");
                }
            }
            EntityOptions entity = new EntityOptions();
            if (nodes.containsKey("policy")) policies.put(resource, nodes.get("policy").text());
            if (nodes.containsKey("operations")) {
                Map<String, List<String>> operations = new LinkedHashMap<>();
                nodes.get("operations").members().forEach((action, node) -> operations.put(action, node.strings()));
                entity.setOperations(operations);
            }
            if (nodes.containsKey("relations")) nodes.get("relations").members().forEach((role, node) -> entity.getRelations().put(role, node.text()));
            if (nodes.containsKey("ownership")) entity.setOwnership(nodes.get("ownership").strings());
            if (nodes.containsKey("fields")) parseFields(resource, nodes.get("fields"));
            if (nodes.containsKey("contracts")) parseContracts(resource, nodes.get("contracts"));
            options.put(resource, entity);
        });
    }

    private void parseActions(String resource, DeclarationNode node) {
        Set<String> names = new java.util.LinkedHashSet<>();
        node.members().forEach((scene, action) -> {
            if (!scene.matches("[A-Za-z][A-Za-z0-9_.-]*")) throw action.error("动作码不规范");
            action.only("name", "entries");
            var attributes = action.members();
            if (!attributes.containsKey("name") || !attributes.containsKey("entries")) throw action.error("动作必须声明 name 和 entries");
            attributes.get("name").text();
            var entries = attributes.get("entries").members();
            if (entries.isEmpty()) throw action.error("动作 entries 不能为空");
            String normalized = scene.toLowerCase(java.util.Locale.ROOT);
            if (!names.add(normalized)) throw action.error("重复动作码: " + scene);
            entries.forEach((entry, policy) -> {
                if (!entry.matches("[A-Za-z][A-Za-z0-9_-]*")) throw policy.error("入口码不规范");
                policy.only("capability", "portals");
                var values = policy.members();
                if (!values.containsKey("capability")) throw policy.error("缺少 capability");
                scenes.add(new com.entloom.crud.core.governance.policy.ScenePolicy(
                    new com.entloom.crud.core.governance.policy.ScenePolicyKey(entry, resource,
                        com.entloom.crud.api.enums.CrudOperationKey.of(com.entloom.crud.api.enums.CommandOperation.ACTION), scene),
                    values.get("capability").text(), values.containsKey("portals") ? new java.util.LinkedHashSet<>(values.get("portals").strings()) : Set.of()));
            });
        });
        actions.put(resource, Set.copyOf(names));
    }

    public Map<String, Set<String>> actions() { return Map.copyOf(actions); }
    public Map<String, Set<com.entloom.crud.api.enums.CrudOperationKey>> allowedOperations() { return Map.copyOf(allowedOperations); }
    public List<com.entloom.crud.core.governance.policy.ScenePolicy> scenes() { return List.copyOf(scenes); }
    public boolean httpExposed(String resource) { return httpExposure.getOrDefault(resource, true); }

    private void parseFields(String resource, DeclarationNode node) {
        Map<String, Map<AuthorizationFields.Use, FieldPlan>> identities = new LinkedHashMap<>();
        node.members().forEach((identity, uses) -> {
            Map<AuthorizationFields.Use, FieldPlan> plans = new LinkedHashMap<>();
            uses.members().forEach((use, plan) -> {
                plan.only("mode", "fields");
                AuthorizationFields.Use key;
                try { key = AuthorizationFields.Use.valueOf(use); }
                catch (IllegalArgumentException exception) { throw plan.error("未知字段用途: " + use); }
                Map<String, DeclarationNode> attributes = plan.members();
                FieldMode mode;
                try { mode = attributes.containsKey("mode") ? FieldMode.valueOf(attributes.get("mode").text()) : FieldMode.EXCLUDE; }
                catch (IllegalArgumentException exception) { throw plan.error("未知字段模式"); }
                List<String> names = attributes.containsKey("fields") ? attributes.get("fields").strings() : List.of();
                if (mode == FieldMode.INCLUDE && names.isEmpty()) throw plan.error("INCLUDE 的 fields 不能为空");
                if ((mode == FieldMode.ALL || mode == FieldMode.NONE) && !names.isEmpty()) throw plan.error("ALL/NONE 不得声明 fields");
                plans.put(key, new FieldPlan(mode, names, plan.source()));
            });
            identities.put(identity, Map.copyOf(plans));
        });
        fields.put(resource, Map.copyOf(identities));
    }

    private void parseContracts(String resource, DeclarationNode node) {
        node.only("create", "update");
        node.members().forEach((operation, contract) -> {
            String key = operation.equals("create") ? "input-required-fields" : "forbidden-fields";
            contract.only(key);
            if (contract.members().containsKey(key)) {
                (operation.equals("create") ? required : forbidden).put(resource, contract.members().get(key).strings());
            }
        });
    }

    public void validate(EntityMetaRegistry entities) {
        Set<String> resources = new java.util.LinkedHashSet<>();
        for (EntityMeta meta : entities.getEntityMetas()) {
            String code = meta.getResourceDescriptor().getResourceCode();
            if (!resources.add(code)) throw new IllegalArgumentException("重复实体资源码: " + code);
        }
        declarations.validateResources("crud", resources);
    }
    public Map<String, EntityPolicyBinding> bindings(EntityMetaRegistry entities) {
        validate(entities);
        Map<String, EntityPolicyBinding> result = new LinkedHashMap<>();
        for (EntityMeta meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            if (policies.containsKey(resource)) result.put(resource, new EntityPolicyBinding(meta.getEntityType(), policies.get(resource), source(resource, "policy"), options.get(resource).getRelations()));
        }
        return result;
    }
    /** HTTP 实体必须有文件与明确访问选择，漏扫描或漏打包不能改变授权路径。 */
    public void assertHttpAccess(EntityMetaRegistry entities) {
        validate(entities);
        for (EntityMeta meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            if (!httpExposed(resource)) continue;
            if (!options.containsKey(resource)) throw new IllegalArgumentException(
                "HTTP 实体缺少声明文件: ent/entities/" + resource + ".yml [实体=" + resource + "]");
            if (!policies.containsKey(resource) && !accessModes.containsKey(resource)) {
                throw new IllegalArgumentException(source(resource) + ": HTTP 实体必须显式选择 policy 或 access: JAVA");
            }
        }
    }
    public String source(String resource) { return declarations.entities().get(resource).members().get("crud").source(); }
    public String source(String resource, String property) {
        return declarations.entities().get(resource).members().get("crud").members().get(property).source();
    }
    public Map<String, EntityOptions> options() { return Map.copyOf(options); }
    public Map<String, Map<AuthorizationFields.Use, FieldPlan>> fields(String resource) { return fields.getOrDefault(resource, Map.of()); }
    public Map<String, List<String>> required() { return Map.copyOf(required); }
    public Map<String, List<String>> forbidden() { return Map.copyOf(forbidden); }
}
