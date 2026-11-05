package com.entloom.crud.starter.config;

import com.entloom.declaration.DeclarationNode;
import com.entloom.declaration.EntityDeclarations;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
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
        private String policy;
        private ActionSelection actionSelection;
        private CrudAccessProperties.Policy access = new CrudAccessProperties.Policy();
        private List<String> ownership;
        private Map<String, List<String>> operations;
        private Map<String, String> relations = new LinkedHashMap<>();
    }
    /** 实体动作：列表替换，或在策略上增减；全局策略仍使用完整列表。 */
    public record ActionSelection(List<String> replacement, List<String> add, List<String> remove) {
        static ActionSelection parse(DeclarationNode node) {
            if (node.isList()) return new ActionSelection(node.strings(), List.of(), List.of());
            node.only("add", "remove");
            var values = node.members();
            return new ActionSelection(null, values.containsKey("add") ? values.get("add").strings() : List.of(),
                values.containsKey("remove") ? values.get("remove").strings() : List.of());
        }
    }
    public enum FieldMode {
        INCLUDE, // 仅包含指定字段
        EXCLUDE, // 排除指定字段
        ALL, // 全部候选字段
        NONE // 关闭该用途
    }
    public record FieldPlan(FieldMode mode, List<String> fields, String source) { }
    private final Map<String, Set<String>> actions = new LinkedHashMap<>();
    private final Map<String, Set<com.entloom.crud.api.enums.CrudOperationKey>> allowedOperations = new LinkedHashMap<>();
    private final Map<String, Boolean> httpExposure = new LinkedHashMap<>();
    private final List<com.entloom.crud.core.governance.policy.ScenePolicy> scenes = new java.util.ArrayList<>();
    private final EntityDeclarations declarations;
    private final Map<String, EntityOptions> options = new LinkedHashMap<>();
    private final Map<String, List<String>> required = new LinkedHashMap<>();
    private final Map<String, List<String>> forbidden = new LinkedHashMap<>();

    public CrudEntityDeclarations(EntityDeclarations declarations) {
        this.declarations = declarations;
        declarations.entities().forEach((resource, root) -> {
            DeclarationNode crud = root.members().get("crud");
            if (crud == null) return;
            crud.only("access", "operations", "relations", "ownership", "contracts", "actions", "allowed-operations", "http-exposed");
            Map<String, DeclarationNode> nodes = crud.members();
            if (nodes.containsKey("http-exposed")) httpExposure.put(resource, nodes.get("http-exposed").bool());
            if (nodes.containsKey("actions")) parseActions(resource, nodes.get("actions"));
            if (nodes.containsKey("allowed-operations")) {
                Set<com.entloom.crud.api.enums.CrudOperationKey> keys = new java.util.LinkedHashSet<>();
                for (String name : nodes.get("allowed-operations").strings()) {
                    if (!keys.add(standardOperation(nodes.get("allowed-operations"), name))) {
                        throw nodes.get("allowed-operations").error("重复标准操作: " + name);
                    }
                }
                allowedOperations.put(resource, Set.copyOf(keys));
            }
            EntityOptions entity = new EntityOptions();
            if (nodes.containsKey("access")) {
                DeclarationNode access = nodes.get("access");
                var values = access.members();
                if (values.containsKey("policy")) entity.setPolicy(values.get("policy").text());
                if (values.containsKey("actions")) entity.setActionSelection(ActionSelection.parse(values.get("actions")));
                values.forEach((identity, node) -> {
                    if (identity.equals("policy") || identity.equals("actions")) return;
                    node.only("actions", "scope", "rules", "fields");
                    var attributes = node.members();
                    var subject = new CrudAccessProperties.Subject();
                    if (attributes.containsKey("actions")) {
                        var actions = attributes.get("actions");
                        if (actions.isList()) subject.setActions(actions.strings());
                        else {
                            actions.only("add", "remove");
                            Map<String, Object> changes = new LinkedHashMap<>();
                            actions.members().forEach((key, value) -> changes.put(key, value.strings()));
                            subject.setActions(changes);
                        }
                    }
                    if (attributes.containsKey("scope")) subject.setScope(attributes.get("scope").text());
                    if (attributes.containsKey("rules")) {
                        Map<String, Object> rules = new LinkedHashMap<>();
                        attributes.get("rules").members().forEach((field, value) -> rules.put(field, declarationValue(value)));
                        subject.setRules(rules);
                    }
                    if (attributes.containsKey("fields")) subject.setFields(parseFields(attributes.get("fields")));
                    entity.getAccess().getAccess().put(identity, subject);
                });
            }

            if (nodes.containsKey("operations")) {
                Map<String, List<String>> operations = new LinkedHashMap<>();
                nodes.get("operations").members().forEach((action, node) -> {
                    Set<String> names = new java.util.LinkedHashSet<>();
                    for (String code : node.strings()) {
                        String name = standardOperation(node, code).getOperation();
                        if (!names.add(name)) throw node.error("重复标准操作: " + code);
                    }
                    operations.put(action, List.copyOf(names));
                });
                entity.setOperations(operations);
            }
            if (nodes.containsKey("relations")) nodes.get("relations").members().forEach((role, node) -> entity.getRelations().put(role, node.text()));
            if (nodes.containsKey("ownership")) entity.setOwnership(nodes.get("ownership").strings());
            if (nodes.containsKey("contracts")) parseContracts(resource, nodes.get("contracts"));
            options.put(resource, entity);
        });
    }

    private static Object declarationValue(DeclarationNode node) {
        if (node.isList()) return node.elements().stream().map(CrudEntityDeclarations::declarationValue).toList();
        try {
            return node.bool();
        } catch (IllegalArgumentException ignored) {
            try {
                return node.number();
            } catch (IllegalArgumentException ignoredInteger) {
                return node.text();
            }
        }
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
    public boolean httpExposed(String resource) { return httpExposure.getOrDefault(resource, options.containsKey(resource)); }

    private static Map<AuthorizationFields.Use, CrudAccessProperties.Field> parseFields(DeclarationNode node) {
        Map<AuthorizationFields.Use, CrudAccessProperties.Field> plans = new LinkedHashMap<>();
        node.members().forEach((use, plan) -> {
            plan.only("mode", "fields");
            AuthorizationFields.Use key = plan.enumValue(AuthorizationFields.Use.class, use);
            var attributes = plan.members();
            var field = new CrudAccessProperties.Field();
            if (attributes.containsKey("mode")) field.setMode(attributes.get("mode").enumValue(FieldMode.class));
            if (attributes.containsKey("fields")) field.setFields(attributes.get("fields").strings());
            if (field.getMode() == FieldMode.INCLUDE && field.getFields().isEmpty()) throw plan.error("INCLUDE 的 fields 不能为空");
            if ((field.getMode() == FieldMode.ALL || field.getMode() == FieldMode.NONE) && !field.getFields().isEmpty())
                throw plan.error("ALL/NONE 不得声明 fields");
            if (plans.putIfAbsent(key, field) != null) throw plan.error("重复字段用途: " + use);
        });
        return plans;
    }

    private static com.entloom.crud.api.enums.CrudOperationKey standardOperation(DeclarationNode node, String code) {
        String constant = code.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        var query = com.entloom.crud.api.enums.QueryOperation.from(constant);
        var command = com.entloom.crud.api.enums.CommandOperation.from(constant);
        if (query == null && (command == null || command == com.entloom.crud.api.enums.CommandOperation.ACTION)) {
            throw node.error("未知标准操作: " + code);
        }
        return query != null ? com.entloom.crud.api.enums.CrudOperationKey.of(query)
            : com.entloom.crud.api.enums.CrudOperationKey.of(command);
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
    /** 已发布实体必须有实际编译的有效策略；policy 还须实际编译，不能因策略组件缺失退回旧治理。 */
    public void assertHttpAccess(EntityMetaRegistry entities, EntityPolicyRegistry compiledPolicies) {
        validate(entities);
        for (EntityMeta meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            if (!httpExposed(resource)) continue;
            if (compiledPolicies == null || !compiledPolicies.containsResource(resource)) {
                throw new IllegalArgumentException(source(resource) + ": HTTP 实体策略未编译: " + options.get(resource).getPolicy() + "，请选择 access.policy 或配置 default-policy");
            }
        }
    }
    public String source(String resource) {
        var root = declarations.entities().get(resource);
        return root == null || !root.members().containsKey("crud") ? "实体 " + resource : root.members().get("crud").source();
    }
    public String source(String resource, String property) {
        var node = declarations.entities().get(resource).members().get("crud");
        for (String part : property.split("\\.")) {
            if (!node.members().containsKey(part)) return node.source() + "." + property;
            node = node.members().get(part);
        }
        return node.source();
    }
    public Map<String, EntityOptions> options() { return Map.copyOf(options); }
    public Map<String, List<String>> required() { return Map.copyOf(required); }
    public Map<String, List<String>> forbidden() { return Map.copyOf(forbidden); }
}
