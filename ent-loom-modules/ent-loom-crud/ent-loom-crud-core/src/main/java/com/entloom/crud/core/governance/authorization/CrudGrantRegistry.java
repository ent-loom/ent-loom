package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudFieldUse;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.crud.annotations.EntCrudGrantScope;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.CrudEntityActions;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 启动时将实体注解编译为操作资格与范围规则；运行时不枚举项目资源、不重新解析注解。 */
public final class CrudGrantRegistry {
    private final List<EntityPolicyTemplate> templates = new ArrayList<EntityPolicyTemplate>();
    private final List<EntityPolicyBinding> bindings = new ArrayList<EntityPolicyBinding>();
    private final Map<BusinessOperation, Map<PolicyIdentityBranch, Grant>> grants =
        new LinkedHashMap<BusinessOperation, Map<PolicyIdentityBranch, Grant>>();

    private final Set<String> resources = new LinkedHashSet<String>();

    private final Map<String, EntityGrantCustomizer> customizers = new LinkedHashMap<String, EntityGrantCustomizer>();

    public CrudGrantRegistry(EntityMetaRegistry entities, BusinessIdentityRegistry identities, CrudGrantDefaults defaults,
                             Collection<EntityGrantCustomizer> extensions, CrudEntityActions actionDefaults) {
        this(entities, identities, defaults, extensions, actionDefaults,
            Collections.<Class<?>, EntCrudGrant[]>emptyMap());
    }

    /** 配置策略先解析为实体声明，与注解复用同一编译及执行链。 */
    public CrudGrantRegistry(EntityMetaRegistry entities, BusinessIdentityRegistry identities, CrudGrantDefaults defaults,
                             Collection<EntityGrantCustomizer> extensions, CrudEntityActions actionDefaults,
                             Map<Class<?>, EntCrudGrant[]> configured) {
        for (String identity : defaults.getOwnerFields().keySet()) requireIdentity(identity, identities);
        for (String identity : defaults.getWildcardIdentities()) requireIdentity(identity, identities);
        for (EntityMeta meta : entities.getEntityMetas()) {
            EntCrudGrant[] declarations = configured.containsKey(meta.getEntityType()) ? configured.get(meta.getEntityType())
                : meta.getEntityType().getAnnotationsByType(EntCrudGrant.class);
            EntCrudEntity entity = meta.getEntityType().getAnnotation(EntCrudEntity.class);
            if (declarations.length == 0 && (entity == null || (!entity.policy().isEmpty() && !configured.containsKey(meta.getEntityType()))
                || defaults.getWildcardIdentities().isEmpty())) continue;
            require(entity != null, meta, "授权必须与 @EntCrudEntity 一起声明");
            require(entity.policy().isEmpty() || configured.containsKey(meta.getEntityType()), meta, "不能同时使用 EntCrudGrant 与策略模板绑定");
            Set<String> entityActions = actionDefaults.resolve(meta.getEntityType());
            String resource = meta.getResourceDescriptor().getResourceCode();
            String source = configured.containsKey(meta.getEntityType())
                ? "ent.loom.crud.policies." + entity.policy() + " / " + resource
                : meta.getEntityType().getName() + "@EntCrudGrant";
            List<EntityPolicyTemplate.Rule> rules = new ArrayList<EntityPolicyTemplate.Rule>();
            AuthorizationFields limit = entityLimit(meta, entity, entities);
            Set<String> explicitIdentities = new LinkedHashSet<String>();
            Set<String> deniedIdentities = new LinkedHashSet<String>();
            for (EntCrudGrant declaration : declarations) {
                String where = declaration.where();
                require(where.isEmpty() || !where.trim().isEmpty(), meta, "where 不能仅包含空白");
                if (!where.isEmpty()) {
                    require(declaration.actions().length == 1 && "view".equals(declaration.actions()[0]), meta,
                        "where 仅支持 view 操作");
                }
                require(declaration.identities().length > 0, meta, "身份不能为空");
                for (String identity : unique(declaration.identities(), meta, "身份")) {
                    if (!"public".equals(identity)) requireIdentity(identity, identities);
                    PolicyIdentityBranch branch = "public".equals(identity) ? PolicyIdentityBranch.publicBranch()
                        : PolicyIdentityBranch.identity(identity);
                    if (declaration.actions().length == 0) {
                        require(!explicitIdentities.contains(identity), meta, "禁止访问不能与同身份授权并存: " + identity);
                        deniedIdentities.add(identity);
                        explicitIdentities.add(identity);
                        continue;
                    }
                    require(!deniedIdentities.contains(identity), meta, "禁止访问不能与同身份授权并存: " + identity);
                    explicitIdentities.add(identity);
                    Grant grant = compile(meta, identity, declaration, defaults, limit);
                    for (String action : unique(declaration.actions(), meta, "操作")) {
                        require(entityActions.contains(action), meta, "授权超出实体操作上限: " + action);
                        BusinessOperation operation = new BusinessOperation(resource, action);
                        Map<PolicyIdentityBranch, Grant> branches = grants.get(operation);
                        if (branches == null) {
                            branches = new LinkedHashMap<PolicyIdentityBranch, Grant>();
                            grants.put(operation, branches);
                        }
                        require(!branches.containsKey(branch), meta, "重复授权: " + action + "/" + identity);
                        branches.put(branch, grant);
                        rules.add(new EntityPolicyTemplate.Rule(action, branch, Collections.<String>emptyList()));
                    }
                }
            }
            // 实体显式声明该身份即整体覆盖全局默认；绝不补回未声明的操作。
            for (String identity : defaults.getWildcardIdentities()) {
                if (explicitIdentities.contains(identity)) continue;
                PolicyIdentityBranch branch = PolicyIdentityBranch.identity(identity);
                Map<AuthorizationFields.Use, Collection<String>> fields = new EnumMap<AuthorizationFields.Use, Collection<String>>(AuthorizationFields.Use.class);
                fields.putAll(limit.snapshot());
                Set<String> expansions = new LinkedHashSet<String>(fields.getOrDefault(AuthorizationFields.Use.EXPAND, Collections.<String>emptyList()));
                expansions.removeIf(name -> !name.contains("."));
                fields.put(AuthorizationFields.Use.EXPAND, expansions);
                Grant grant = new Grant(meta, AuthorizationScope.all(), null, null, AuthorizationFields.of(fields));
                for (String action : entityActions) {
                    BusinessOperation operation = new BusinessOperation(resource, action);
                    grants.computeIfAbsent(operation, key -> new LinkedHashMap<PolicyIdentityBranch, Grant>()).put(branch, grant);
                    rules.add(new EntityPolicyTemplate.Rule(action, branch, Collections.<String>emptyList()));
                }
            }
            resources.add(resource);
            // 复用统一策略内核，不建立另一条授权执行链。
            String code = "grant:" + meta.getEntityType().getName();
            templates.add(new EntityPolicyTemplate(code, source, rules));
            bindings.add(new EntityPolicyBinding(meta.getEntityType(), code, source, Collections.<String, String>emptyMap()));
        }
        for (EntityGrantCustomizer extension : extensions) {
            EntityMeta meta = entities.getEntityMeta(extension.getEntityType());
            if (meta == null || !resources.contains(meta.getResourceDescriptor().getResourceCode())) {
                throw new ValidationException("动态授权扩展必须绑定已纳入声明式授权的实体: " + extension.getEntityType());
            }
            String resource = meta.getResourceDescriptor().getResourceCode();
            if (customizers.put(resource, extension) != null) throw new ValidationException("实体动态授权扩展重复: " + resource);
        }
    }

    public boolean governs(String resource) { return resources.contains(resource); }

    public List<EntityPolicyTemplate> getTemplates() { return Collections.unmodifiableList(templates); }
    public List<EntityPolicyBinding> getBindings() { return Collections.unmodifiableList(bindings); }

    public BusinessPermissionDecision evaluate(VisitorContext visitor, BusinessOperation operation,
                                               EntityPolicyRegistry.EffectiveRule rule, String targetId) {
        Map<PolicyIdentityBranch, Grant> branches = grants.get(operation);
        Grant grant = branches == null ? null : branches.get(rule.getBranch());
        if (grant == null) return BusinessPermissionDecision.deny();
        AuthorizationScope scope = grant.scope;
        if (scope == null) {
            BusinessIdentity identity = visitor.getIdentity().orElse(null);
            if (identity == null) return BusinessPermissionDecision.deny();
            scope = AuthorizationScope.equal(grant.ownerField, scalar(identity.getIdentityId(), grant.ownerType, grant.entity));
        }
        if (!grant.where.isEmpty()) scope = AuthorizationScope.intersect(scope, AuthorizationScope.sqlWhere(grant.where));
        BusinessPermissionDecision declared = BusinessPermissionDecision.allow(scope, grant.fields);
        EntityGrantCustomizer extension = customizers.get(operation.getResourceCode());
        if (extension == null) return declared;
        BusinessPermissionDecision decision = extension.customize(visitor, operation, targetId, declared);
        if (decision == null) return BusinessPermissionDecision.deny();
        if (decision.getKind() != BusinessPermissionDecision.Kind.ALLOW) return decision;
        return BusinessPermissionDecision.allow(AuthorizationScope.intersect(scope, decision.getScope()),
            decision.getFields().intersect(grant.fields));
    }

    private static Grant compile(EntityMeta meta, String identity, EntCrudGrant declaration,
                                 CrudGrantDefaults defaults, AuthorizationFields limit) {
        AuthorizationScope scope = null;
        String field = declaration.scopeField();
        if (declaration.scope() == EntCrudGrantScope.ALL) {
            require(field.isEmpty(), meta, "全量基础范围不能声明归属字段");
            scope = AuthorizationScope.all();
        } else {
            require(!"public".equals(identity), meta, "匿名授权必须显式声明 ALL 范围");
            if (field.isEmpty() && defaults.getWildcardIdentities().contains(identity)) scope = AuthorizationScope.all();
            else {
                if (field.isEmpty()) field = defaults.getOwnerFields().get(identity);
                requireScopeField(meta, field);
            }
        }
        return new Grant(meta, scope, field, scope == null ? meta.resolveFieldMeta(field).getJavaType() : null,
            compileFields(meta, declaration, limit), declaration.where());
    }

    private static AuthorizationFields compileFields(EntityMeta meta, EntCrudGrant declaration, AuthorizationFields limit) {
        Map<AuthorizationFields.Use, Collection<String>> fields = new EnumMap<AuthorizationFields.Use, Collection<String>>(AuthorizationFields.Use.class);
        Map<AuthorizationFields.Use, Set<String>> restrictions = new EnumMap<AuthorizationFields.Use, Set<String>>(AuthorizationFields.Use.class);
        Set<AuthorizationFields.Use> explicitUses = new LinkedHashSet<AuthorizationFields.Use>();
        boolean seenAll = false;
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
            fields.put(use, projection(limit, use));
        }
        for (EntCrudGrant.Fields restriction : declaration.fields()) {
            require(restriction.use().length > 0, meta, "字段用途不能为空");
            Set<EntCrudFieldUse> selected = new LinkedHashSet<EntCrudFieldUse>();
            for (EntCrudFieldUse use : restriction.use()) require(selected.add(use), meta, "字段用途重复: " + use);
            boolean all = selected.contains(EntCrudFieldUse.ALL);
            require(!all || selected.size() == 1, meta, "ALL 不能与其他字段用途混写");
            if (all) {
                require(!seenAll, meta, "ALL 字段规则重复");
                seenAll = true;
            }
            require(restriction.value().length == 0 || restriction.exclude().length == 0, meta, "字段白名单和排除不能同时声明");
            boolean excluding = restriction.exclude().length > 0;
            Set<String> names = unique(excluding ? restriction.exclude() : restriction.value(), meta, "授权字段");
            if (all) {
                for (String name : names) {
                    boolean known = excluding && meta.resolveFieldMeta(name) != null;
                    for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) known |= limit.allows(use, name);
                    require(known, meta, "统一授权引用未知或超出实体上限的字段: " + name);
                }
            }
            for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
                if (!all && !selected.contains(EntCrudFieldUse.valueOf(use.name()))) continue;
                if (!all) {
                    require(explicitUses.add(use), meta, "字段用途重复: " + use);
                    for (String name : names) {
                        require(limit.allows(use, name), meta, "授权字段超出实体上限: " + use + "/" + name);
                        require(excluding || use != AuthorizationFields.Use.EXPAND || name.contains("."), meta,
                            "关联授权必须声明逐字段投影: " + name);
                    }
                }
                Set<String> allowed = projection(limit, use);
                if (excluding) allowed.removeAll(names);
                else allowed.retainAll(names);
                Set<String> previous = restrictions.get(use);
                if (previous == null) restrictions.put(use, allowed);
                else previous.retainAll(allowed);
            }
        }
        fields.putAll(restrictions);
        return AuthorizationFields.of(fields);
    }

    /** 所有规则都从用途上限收窄，不能借统一字段白名单开放主键写入或完整关联。 */
    private static Set<String> projection(AuthorizationFields limit, AuthorizationFields.Use use) {
        Set<String> fields = new LinkedHashSet<String>(limit.snapshot().getOrDefault(use, Collections.<String>emptySet()));
        if (use == AuthorizationFields.Use.EXPAND) fields.removeIf(name -> !name.contains("."));
        return fields;
    }

    private static AuthorizationFields entityLimit(EntityMeta meta, EntCrudEntity entity, EntityMetaRegistry entities) {
        AuthorizationFields metadata = EntityPolicyRegistry.compileFieldLimit(meta, entities);
        if (entity.fieldLimits().length == 0) return metadata;
        Map<AuthorizationFields.Use, Collection<String>> fields = new EnumMap<AuthorizationFields.Use, Collection<String>>(AuthorizationFields.Use.class);
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) fields.put(use, Collections.<String>emptyList());
        Set<AuthorizationFields.Use> seen = new LinkedHashSet<AuthorizationFields.Use>();
        for (String entry : entity.fieldLimits()) {
            int separator = entry.indexOf(':');
            require(separator > 0, meta, "字段上限格式应为 USE:field1,field2");
            AuthorizationFields.Use use;
            try { use = AuthorizationFields.Use.valueOf(entry.substring(0, separator)); }
            catch (IllegalArgumentException failure) { throw new ValidationException("未知字段用途: " + entry); }
            require(seen.add(use), meta, "字段上限用途重复: " + use);
            String value = entry.substring(separator + 1);
            Set<String> names = unique(value.isEmpty() ? new String[0] : value.split(",", -1), meta, "上限字段");
            for (String name : names) require(metadata.allows(use, name), meta, "字段上限超出实体元数据: " + use + "/" + name);
            fields.put(use, names);
        }
        return AuthorizationFields.of(fields);
    }

    private static EntityFieldMeta requireScopeField(EntityMeta meta, String field) {
        EntityFieldMeta metadata = field == null ? null : meta.resolveFieldMeta(field);
        require(metadata != null && !metadata.isRelation() && metadata.getColumnName() != null, meta, "授权范围字段不存在或不是持久化标量: " + field);
        return metadata;
    }

    private static Object scalar(String value, Class<?> type, EntityMeta meta) {
        try {
            if (type == String.class) return value;
            if (type == Boolean.class || type == boolean.class) {
                if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException();
                return Boolean.valueOf(value);
            }
            if (type == Long.class || type == long.class) return Long.valueOf(value);
            if (type == Integer.class || type == int.class) return Integer.valueOf(value);
            if (type == Short.class || type == short.class) return Short.valueOf(value);
            if (type == Byte.class || type == byte.class) return Byte.valueOf(value);
            if (type == BigDecimal.class) return new BigDecimal(value);
            if (type == BigInteger.class) return new BigInteger(value);
            if (type == java.util.UUID.class) return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new ValidationException("身份归属值与字段类型不匹配: " + meta.getEntityType().getName() + "/" + type.getName() + "/" + value);
        }
        throw new ValidationException("身份归属不支持字段类型: " + type.getName());
    }

    private static Set<String> unique(String[] values, EntityMeta meta, String label) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) {
            require(value != null && !value.trim().isEmpty() && value.equals(value.trim()), meta, label + "不能为空或含首尾空格");
            require(result.add(value), meta, label + "重复: " + value);
        }
        return result;
    }

    private static void requireIdentity(String identity, BusinessIdentityRegistry identities) {
        if (!identities.snapshot().containsKey(identity)) throw new ValidationException("授权引用未注册身份: " + identity);
    }

    private static void require(boolean condition, EntityMeta meta, String message) {
        if (!condition) throw new ValidationException(meta.getEntityType().getName() + "@EntCrudGrant: " + message);
    }

    private static final class Grant {
        private final EntityMeta entity;
        private final AuthorizationScope scope;
        private final String ownerField;
        private final Class<?> ownerType;
        private final AuthorizationFields fields;
        private final String where;
        private Grant(EntityMeta entity, AuthorizationScope scope, String ownerField, Class<?> ownerType, AuthorizationFields fields) {
            this(entity, scope, ownerField, ownerType, fields, "");
        }
        private Grant(EntityMeta entity, AuthorizationScope scope, String ownerField, Class<?> ownerType,
                      AuthorizationFields fields, String where) {
            this.where = where;
            this.entity = entity;
            this.scope = scope;
            this.ownerField = ownerField;
            this.ownerType = ownerType;
            this.fields = fields;
        }
    }
}
