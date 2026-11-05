package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudAccess;
import com.entloom.crud.annotations.EntCrudAccessField;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Locale;

/** CRUD 自行解释实体节点，产物继续交给既有策略、归属与输入契约编译器。 */
public final class CrudEntityDeclarations {

    @lombok.Data
    public static class EntityOptions {
        private String policy;
        private ActionSelection actionSelection;
        private CrudAccessProperties.Policy access = new CrudAccessProperties.Policy();
        private List<String> ownership;
        private Map<String, List<String>> operations;
        private Map<AuthorizationFields.Use, List<String>> fieldLimits;
        private Map<String, String> relations = new LinkedHashMap<>();
    }
    /** 实体动作：列表替换，或在策略上增减；全局策略仍使用完整列表。 */
    public record ActionSelection(List<String> replacement, List<String> add, List<String> remove) { }
    public enum FieldMode {
        INCLUDE, // 仅包含指定字段
        EXCLUDE, // 排除指定字段
        ALL, // 全部候选字段
        NONE // 关闭该用途
    }
    public record FieldPlan(FieldMode mode, List<String> fields, String source) { }
    private final Map<String, Set<String>> actions = new LinkedHashMap<>();
    private final Map<String, Boolean> httpExposure = new LinkedHashMap<>();
    private final Map<String, EntityOptions> options = new LinkedHashMap<>();
    private final Map<String, List<String>> required = new LinkedHashMap<>();
    private final Map<String, List<String>> forbidden = new LinkedHashMap<>();

    public CrudEntityDeclarations() { }

    /** 从实体类治理注解构建声明；运行时不依赖实体 YAML。 */
    public CrudEntityDeclarations(java.util.Collection<Class<?>> entityTypes) {
        if (entityTypes == null) return;
        for (Class<?> entityType : entityTypes) {
            EntCrudEntity annotation = entityType.getAnnotation(EntCrudEntity.class);
            if (annotation == null) continue;
            String resource = resourceCode(entityType, annotation);
            require(!options.containsKey(resource), "重复实体资源码: " + resource + "，类型: " + entityType.getName());
            require(!(annotation.actionsSpecified() || annotation.actions().length > 0) || (annotation.addActions().length == 0 && annotation.removeActions().length == 0),
                "实体动作不能同时替换和增减: " + entityType.getName());
            EntityOptions entity = new EntityOptions();
            entity.setPolicy(trimToNull(annotation.policy()));
            if (annotation.actionsSpecified() || annotation.actions().length > 0 || annotation.addActions().length > 0 || annotation.removeActions().length > 0) {
                entity.setActionSelection(new ActionSelection(
                    annotation.actionsSpecified() || annotation.actions().length > 0 ? List.of(annotation.actions()) : null,
                    List.of(annotation.addActions()), List.of(annotation.removeActions())));
                if (annotation.actionsSpecified() || annotation.actions().length > 0) {
                    actions.put(resource, new LinkedHashSet<>(List.of(annotation.actions())));
                }
            }
            if (annotation.ownershipSpecified() || annotation.ownership().length > 0) entity.setOwnership(List.of(annotation.ownership()));
            Map<String, String> relations = new LinkedHashMap<>();
            for (String pair : annotation.relations()) {
                String[] parts = pair.split("=", -1);
                require(parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank(),
                    "@EntCrudEntity.relations 必须使用 role=field: " + entityType.getName());
                require(relations.putIfAbsent(parts[0].trim(), parts[1].trim()) == null,
                    "重复关系角色: " + entityType.getName() + "#" + parts[0].trim());
            }
            entity.setRelations(relations);
            if (annotation.fieldLimits().length > 0) entity.setFieldLimits(parseFieldLimits(annotation.fieldLimits(), entityType));
            if (annotation.operations().length > 0) {
                Map<String, List<String>> operations = parseOperations(annotation.operations(), entityType);
                entity.setOperations(operations);
            }
            options.put(resource, entity);
            httpExposure.put(resource, annotation.httpExposed());
            required.put(resource, List.of(annotation.requiredCreateFields()));
            forbidden.put(resource, List.of(annotation.forbiddenUpdateFields()));
            for (EntCrudAccess access : entityType.getAnnotationsByType(EntCrudAccess.class)) {
                require(!access.identity().isBlank(), "身份不能为空: " + entityType.getName());
                require(!(access.actionsSpecified() || access.actions().length > 0)
                        || (access.addActions().length == 0 && access.removeActions().length == 0),
                    "身份动作不能同时替换和增减: " + entityType.getName() + "/" + access.identity());
                CrudAccessProperties.Subject subject = new CrudAccessProperties.Subject();
                if (access.actionsSpecified() || access.actions().length > 0) subject.setActions(List.of(access.actions()));
                else if (access.addActions().length > 0 || access.removeActions().length > 0) {
                    Map<String, Object> changes = new LinkedHashMap<>();
                    changes.put("add", List.of(access.addActions()));
                    changes.put("remove", List.of(access.removeActions()));
                    subject.setActions(changes);
                }
                if (!access.scope().isBlank()) subject.setScope(access.scope());
                Map<String, Object> rules = parseRules(access.rules(), entityType);
                if (!rules.isEmpty()) subject.setRules(rules);
                Map<AuthorizationFields.Use, CrudAccessProperties.Field> fields = new LinkedHashMap<>();
                for (EntCrudAccessField field : access.fields()) {
                    AuthorizationFields.Use use = AuthorizationFields.Use.valueOf(field.use().trim().toUpperCase(Locale.ROOT));
                    CrudAccessProperties.Field plan = new CrudAccessProperties.Field();
                    plan.setMode(FieldMode.valueOf(field.mode().trim().toUpperCase(Locale.ROOT)));
                    plan.setFields(List.of(field.fields()));
                    require(plan.getMode() != FieldMode.INCLUDE || !plan.getFields().isEmpty(), "INCLUDE 的 fields 不能为空: " + entityType.getName());
                    require((plan.getMode() != FieldMode.ALL && plan.getMode() != FieldMode.NONE) || plan.getFields().isEmpty(),
                        "ALL/NONE 不得声明 fields: " + entityType.getName());
                    require(fields.putIfAbsent(use, plan) == null, "重复字段用途: " + entityType.getName() + "/" + use);
                }
                subject.setFields(fields);
                require(entity.getAccess().getAccess().putIfAbsent(access.identity(), subject) == null,
                    "重复身份分支: " + entityType.getName() + "/" + access.identity());
            }
        }
    }

    private static String resourceCode(Class<?> type, EntCrudEntity crud) {
        if (crud != null && crud.name() != null && !crud.name().trim().isEmpty()) return crud.name().trim();
        EntEntity meta = type.getAnnotation(EntEntity.class);
        if (meta != null && meta.entity() != null && !meta.entity().trim().isEmpty()) return meta.entity().trim();
        String simpleName = type.getSimpleName();
        if (meta == null) return simpleName;
        int prefix = 0;
        while (prefix < simpleName.length() && Character.isUpperCase(simpleName.charAt(prefix))) prefix++;
        if (prefix > 1 && prefix < simpleName.length()) prefix--;
        return simpleName.substring(0, prefix).toLowerCase(Locale.ROOT) + simpleName.substring(prefix);
    }

    private static Map<AuthorizationFields.Use, List<String>> parseFieldLimits(String[] values, Class<?> type) {
        Map<AuthorizationFields.Use, List<String>> result = new LinkedHashMap<>();
        for (String value : values) {
            String[] parts = value.split(":", 2);
            require(parts.length == 2, "@EntCrudEntity.fieldLimits 必须使用 USE:field1,field2: " + type.getName());
            AuthorizationFields.Use use = AuthorizationFields.Use.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            List<String> fields = parts[1].isBlank() ? List.of() : Arrays.stream(parts[1].split(",", -1)).map(String::trim).toList();
            require(fields.stream().noneMatch(String::isBlank) && new LinkedHashSet<>(fields).size() == fields.size(),
                "字段上限包含空字段或重复字段: " + type.getName());
            require(result.putIfAbsent(use, fields) == null, "重复字段用途: " + type.getName() + "/" + use);
        }
        return result;
    }

    private static Map<String, List<String>> parseOperations(String[] values, Class<?> type) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String value : values) {
            String[] parts = value.split("=", 2);
            require(parts.length == 2, "@EntCrudEntity.operations 必须使用 action=OP1,OP2: " + type.getName());
            require(!parts[0].isBlank(), "操作动作不能为空: " + type.getName());
            require(result.putIfAbsent(parts[0].trim(), parts[1].isBlank() ? List.of() : Arrays.stream(parts[1].split(",", -1)).map(String::trim).toList()) == null,
                "重复操作动作: " + type.getName() + "/" + parts[0].trim());
        }
        return result;
    }

    private static Map<String, Object> parseRules(String[] values, Class<?> type) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String value : values) {
            String[] parts = value.split("=", 2);
            require(parts.length == 2, "@EntCrudAccess.rules 必须使用 field=value: " + type.getName());
            String raw = parts[1].trim();
            Object parsed = "true".equalsIgnoreCase(raw) ? Boolean.TRUE : "false".equalsIgnoreCase(raw) ? Boolean.FALSE : raw;
            require(!parts[0].isBlank() && !raw.isBlank(), "规则字段和值不能为空: " + type.getName());
            require(result.putIfAbsent(parts[0].trim(), parsed) == null, "重复规则字段: " + type.getName() + "/" + parts[0].trim());
        }
        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public Map<String, Set<String>> actions() { return Map.copyOf(actions); }
    public boolean httpExposed(String resource) { return httpExposure.getOrDefault(resource, options.containsKey(resource)); }

    public void validate(EntityMetaRegistry entities) {
        Set<String> resources = new java.util.LinkedHashSet<>();
        for (EntityMeta meta : entities.getEntityMetas()) {
            String code = meta.getResourceDescriptor().getResourceCode();
            if (!resources.add(code)) throw new IllegalArgumentException("重复实体资源码: " + code);
        }
        for (String resource : options.keySet()) {
            if (!resources.contains(resource)) throw new IllegalArgumentException(source(resource) + ": 实体未注册或未扫描: " + resource);
        }
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
    public String source(String resource) { return "@EntCrudEntity(" + resource + ")"; }
    public String source(String resource, String property) { return source(resource) + "." + property; }
    public Map<String, EntityOptions> options() { return Map.copyOf(options); }
    public Map<String, List<String>> required() { return Map.copyOf(required); }
    public Map<String, List<String>> forbidden() { return Map.copyOf(forbidden); }
}
