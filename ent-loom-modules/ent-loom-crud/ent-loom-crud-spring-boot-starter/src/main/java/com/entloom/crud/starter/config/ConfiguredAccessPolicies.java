package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.ConfiguredBusinessIdentity;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.fact.RelationFactResolver;
import com.entloom.crud.core.governance.fact.RelationFactSet;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 将配置冻结为现有 Core 声明；与 Java、注解来源统一校验，不建立第二条执行链。 */
public final class ConfiguredAccessPolicies {
    private static final String SOURCE = "ent.loom.crud.access";
    /** 仅标准动作具有缺省映射，批量、保存及业务动作须显式登记。 */
    private static final Map<String, List<String>> DEFAULT_OPERATIONS = Map.of(
        "view", List.of("LIST", "PAGE", "FIND_ONE", "DETAIL"),
        "create", List.of("CREATE"),
        "update", List.of("UPDATE"),
        "delete", List.of("DELETE"));
    private final List<EntityPolicyTemplate> templates = new ArrayList<>();
    private final List<EntityPolicyBinding> bindings = new ArrayList<>();
    private final List<BusinessOperationDeclaration> operations = new ArrayList<>();
    private final List<CrudOperationMapping> mappings = new ArrayList<>();
    private final Map<Key, AuthorizationFields> fields = new LinkedHashMap<>();
    private final Map<Key, Function<BusinessIdentity, Object>> identityReaders = new LinkedHashMap<>();

    public ConfiguredAccessPolicies(CrudAccessProperties config, EntityMetaRegistry entities,
        BusinessIdentityRegistry identities, ConfiguredBusinessIdentities definitions) {
        Map<String, EntityMeta> metas = new LinkedHashMap<>();
        for (EntityMeta meta : entities.getEntityMetas()) {
            metas.put(meta.getResourceDescriptor().getResourceCode(), meta);
        }
        for (var policy : config.getPolicies().entrySet()) {
            name(policy.getKey(), "策略");
            require(!policy.getValue().isEmpty(), "策略没有身份分支: " + policy.getKey());
            List<EntityPolicyTemplate.Rule> rules = new ArrayList<>();
            for (var branch : policy.getValue().entrySet()) {
                String identity = branch.getKey();
                require(identities.snapshot().containsKey(identity), "策略引用未知身份: " + identity);
                var declaration = branch.getValue();
                String scope = defaultName(declaration.getScope(), identity);
                require(config.getRelations().containsKey(scope), "缺少安全默认关系范围: " + scope);
                for (String action : unique(declaration.getActions(), "动作 " + policy.getKey() + "/" + identity)) {
                    rules.add(new EntityPolicyTemplate.Rule(action, PolicyIdentityBranch.identity(identity), List.of(scope)));
                }
            }
            templates.add(new EntityPolicyTemplate(policy.getKey(), SOURCE + ".policies." + policy.getKey(), rules));
        }
        Map<String, String> policyBindings = resolvePolicyBindings(config);
        Map<String, CrudAccessProperties.EntityOptions> declarations = config.entityDeclarations();
        Set<String> resources = new LinkedHashSet<>(declarations.keySet());
        resources.addAll(policyBindings.keySet());
        // 仅对实体声明或批量绑定明确选择的资源开放，不扫描其他同名实体。
        for (String resource : resources) {
            EntityMeta meta = metas.get(resource);
            require(meta != null, "策略绑定未知实体: " + resource);
            var declaration = declarations.getOrDefault(resource, new CrudAccessProperties.EntityOptions());
            String batchPolicy = policyBindings.get(resource);
            String declarationSource = SOURCE + (config.getEntityOptions().containsKey(resource)
                ? ".entity-options." : ".entities.") + resource;
            // 登记接口或实体差异不授予权限；未选择策略时仍由既有治理规则授权。
            if (batchPolicy == null && declaration.getPolicy() == null && declaration.getOperations() == null
                && !config.getPolicies().containsKey(resource)) {
                require(declaration.getRelations().isEmpty(), "未选策略的实体不能声明策略关系: " + resource);
                require(declaration.getFields().isEmpty(), "未选策略的实体不能声明字段方案: " + resource);
                continue;
            }
            String policy = defaultName(declaration.getPolicy(), batchPolicy == null ? resource : batchPolicy);
            require(batchPolicy == null || batchPolicy.equals(policy), "实体策略与批量绑定冲突: " + resource
                + " -> " + policy + " / " + batchPolicy);
            var branches = config.getPolicies().get(policy);
            require(branches != null, "实体绑定未知策略: " + resource + " -> " + policy);
            Map<String, List<String>> operationCatalog = resolveOperations(declaration, branches, resource);
            String operationSource = declaration.getOperations() == null
                ? SOURCE + ".policies." + policy : declarationSource + ".operations";
            for (String identity : declaration.getFields().keySet()) {
                require(branches.containsKey(identity), "实体字段方案引用未使用的身份: " + resource + "/" + identity);
            }
            Map<String, String> relations = new LinkedHashMap<>();
            for (var branch : branches.entrySet()) {
                String identity = branch.getKey();
                String role = defaultName(branch.getValue().getScope(), identity);
                var relation = config.getRelations().get(role);
                String field = declaration.getRelations().getOrDefault(role, defaultName(relation.getField(), role + "Id"));
                relations.put(role, field);
                var definition = definitions.definitions().get(identity);
                String identityField = defaultName(relation.getIdentityField(),
                    definition == null ? role + "Id" : definition.getIdField());
                Function<BusinessIdentity, Object> reader;
                if (definition != null) {
                    require(definition.getIdField().equals(identityField), "关系引用未知身份属性: " + identity + "/" + identityField);
                    var entityField = meta.resolveFieldMeta(field);
                    require(entityField != null && definition.getIdType().getJavaType()
                        == org.springframework.util.ClassUtils.resolvePrimitiveIfNecessary(entityField.getJavaType()),
                        "身份 ID 类型与实体关系字段不匹配: " + identity + " -> " + resource + "/" + field);
                    reader = snapshot -> {
                        require(snapshot instanceof ConfiguredBusinessIdentity
                            && definition.equals(((ConfiguredBusinessIdentity) snapshot).getDefinition()), "当前身份结构与定义不匹配: " + identity);
                        return ((ConfiguredBusinessIdentity) snapshot).read(identityField);
                    };
                } else {
                    reader = reader(identities.snapshot().get(identity).getIdentityType(), identityField);
                }
                AuthorizationFields fieldPlan = compileFields(config, declaration, meta, resource, identity);
                for (String action : branch.getValue().getActions()) {
                    require(operationCatalog.containsKey(action), "策略动作未在实体操作目录注册: " + resource + "/" + action);
                    Key key = new Key(new BusinessOperation(resource, action), identity);
                    fields.put(key, fieldPlan);
                    identityReaders.put(key, reader);
                }
            }
            for (String role : declaration.getRelations().keySet()) {
                require(relations.containsKey(role), "实体绑定未使用的关系: " + resource + "/" + role);
            }
            String bindingSource = batchPolicy == null ? declarationSource
                : SOURCE + ".policy-bindings." + batchPolicy;
            bindings.add(new EntityPolicyBinding(meta.getEntityType(), policy, bindingSource, relations));
            for (var action : operationCatalog.entrySet()) {
                BusinessOperation operation = new BusinessOperation(resource, name(action.getKey(), "动作"));
                operations.add(new BusinessOperationDeclaration(operation, operationSource));
                Set<String> seen = new LinkedHashSet<>();
                for (String shape : action.getValue()) {
                    require(seen.add(shape), "重复 CRUD 映射: " + operation + "/" + shape);
                    mappings.add(new CrudOperationMapping(operationKey(shape), null, operation, operationSource));
                }
            }
        }
        require(config.getPolicies().isEmpty() || !bindings.isEmpty(), "策略配置没有实体消费者");
    }

    public List<EntityPolicyTemplate> templates() { return List.copyOf(templates); }
    public List<EntityPolicyBinding> bindings() { return List.copyOf(bindings); }
    public List<BusinessOperationDeclaration> operations() { return List.copyOf(operations); }
    public List<CrudOperationMapping> mappings() { return List.copyOf(mappings); }

    /** 批量声明只展开绑定，不修改属性对象，也不合并或覆盖策略权限。 */
    private static Map<String, String> resolvePolicyBindings(CrudAccessProperties config) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var binding : config.getPolicyBindings().entrySet()) {
            String policy = name(binding.getKey(), "策略");
            require(config.getPolicies().containsKey(policy), "批量绑定未知策略: " + policy);
            for (String resource : unique(binding.getValue(), "策略绑定 " + policy)) {
                String previous = result.putIfAbsent(resource, policy);
                require(previous == null, "实体重复批量绑定: " + resource + " -> " + previous + " / " + policy);
            }
        }
        return result;
    }

    /** 显式目录整体覆盖；缺省只展开策略已声明的标准动作，不增加授权动作。 */
    private static Map<String, List<String>> resolveOperations(CrudAccessProperties.EntityOptions entity,
        Map<String, CrudAccessProperties.Branch> branches, String resource) {
        if (entity.getOperations() != null) return entity.getOperations();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (CrudAccessProperties.Branch branch : branches.values()) {
            for (String action : branch.getActions()) {
                List<String> shapes = DEFAULT_OPERATIONS.get(action);
                require(shapes != null, "业务动作须显式声明 operations: " + resource + "/" + action);
                result.putIfAbsent(action, shapes);
            }
        }
        return result;
    }

    /** 字段差异归实体；同一模板可绑定不同结构的实体。 */
    private static AuthorizationFields compileFields(CrudAccessProperties config, CrudAccessProperties.EntityOptions entity,
        EntityMeta meta, String resource, String identity) {
        String planName = entity.getFields().containsKey(identity)
            ? name(entity.getFields().get(identity), "字段方案") : resource;
        var plan = config.getFieldPlans().get(planName);
        require(plan != null || !entity.getFields().containsKey(identity), "缺少字段方案: " + resource + "/" + identity + " -> " + planName);
        if (plan == null) return AuthorizationFields.of(Map.of());
        for (var use : plan.entrySet()) {
            List<String> names = use.getValue();
            // 空 RETURN/FILTER 明确关闭能力，其他用途仍要求非空声明。
            if ((use.getKey() == AuthorizationFields.Use.RETURN || use.getKey() == AuthorizationFields.Use.FILTER)
                && names != null && names.isEmpty()) continue;
            for (String field : unique(names, "字段方案 " + planName + "/" + use.getKey())) {
                require(meta.getAllowedFields().contains(field), "字段方案引用未知字段: " + resource + "/" + field);
            }
        }
        return AuthorizationFields.of(plan);
    }

    /** YAML 授权显式动作与实体字段方案（含 RETURN/FILTER 缺省）；项目动态判断只能进一步收窄或拒绝。 */
    public BusinessPermissionEvaluator evaluator(BusinessPermissionEvaluator dynamic) {
        return (visitor, operation, rule, target) -> {
            AuthorizationFields allowed = fields.get(key(rule));
            if (allowed == null) {
                return dynamic == null ? BusinessPermissionDecision.deny() : dynamic.evaluate(visitor, operation, rule, target);
            }
            allowed = allowed.resolveDefaults(rule.getFieldLimit());
            if (dynamic == null) return BusinessPermissionDecision.allow(AuthorizationScope.all(), allowed);
            var decision = dynamic.evaluate(visitor, operation, rule, target);
            return decision == null || decision.getKind() != BusinessPermissionDecision.Kind.ALLOW
                ? BusinessPermissionDecision.deny()
                : BusinessPermissionDecision.allow(decision.getScope(),
                    allowed.intersect(decision.getFields().resolveDefaults(rule.getFieldLimit())));
        };
    }

    /** 简单关系只读取当前可信身份；复杂 Java 关系仍交给项目解析器。 */
    public RelationFactResolver relations(RelationFactResolver custom) {
        return (visitor, rule, target, limit) -> {
            Function<BusinessIdentity, Object> reader = identityReaders.get(key(rule));
            if (reader == null) {
                if (custom == null) throw new PermissionDeniedException("关系事实解析器未配置: " + rule.getOperation());
                return custom.resolve(visitor, rule, target, limit);
            }
            BusinessIdentity identity = visitor.getIdentity().orElseThrow(() -> new PermissionDeniedException("缺少当前身份"));
            try {
                Object value = reader.apply(identity);
                if (value == null) throw new PermissionDeniedException("当前身份缺少关系属性: " + identity.getIdentityCode());
                String role = rule.getRelationFields().keySet().iterator().next();
                return new RelationFactSet(List.of(Map.of(role, value.toString())));
            } catch (RuntimeException ex) {
                throw new PermissionDeniedException("无法读取可信身份关系属性: " + identity.getIdentityCode());
            }
        };
    }

    private static Key key(EntityPolicyRegistry.EffectiveRule rule) {
        return new Key(rule.getOperation(), rule.getBranch().getIdentityCode());
    }

    private static Function<BusinessIdentity, Object> reader(Class<?> type, String field) {
        for (String method : List.of(field, "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1))) {
            try {
                Method reader = type.getMethod(method);
                require(!java.lang.reflect.Modifier.isStatic(reader.getModifiers()) && reader.getReturnType() != void.class,
                    "身份属性必须为实例只读属性: " + field);
                Class<?> valueType = reader.getReturnType();
                require(CharSequence.class.isAssignableFrom(valueType) || Number.class.isAssignableFrom(valueType)
                    || valueType == java.util.UUID.class || valueType == byte.class || valueType == short.class
                    || valueType == int.class || valueType == long.class || valueType == float.class
                    || valueType == double.class, "简单关系身份属性必须为标量: " + field);
                return identity -> {
                    try {
                        return reader.invoke(identity);
                    } catch (ReflectiveOperationException exception) {
                        throw new PermissionDeniedException("无法读取可信身份关系属性: " + field);
                    }
                };
            } catch (NoSuchMethodException ignored) {
                // 同时支持 record 属性访问器与 JavaBean getter。
            }
        }
        throw new ValidationException("关系引用未知身份属性: " + type.getName() + "/" + field);
    }

    private static CrudOperationKey operationKey(String shape) {
        QueryOperation query = QueryOperation.from(shape);
        if (query != null) return CrudOperationKey.of(query);
        CommandOperation command = CommandOperation.from(shape);
        require(command != null && command != CommandOperation.ACTION, "不支持的声明式 CRUD 映射: " + shape);
        return CrudOperationKey.of(command);
    }

    private static List<String> unique(List<String> values, String label) {
        require(values != null && !values.isEmpty(), label + " 不能为空");
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) require(seen.add(name(value, label)), label + " 重复: " + value);
        return values;
    }

    private static String defaultName(String value, String fallback) { return value == null ? fallback : name(value, "声明"); }
    private static String name(String value, String label) {
        require(value != null && !value.isBlank() && value.equals(value.trim()), label + " 名称不能为空或含首尾空格");
        return value;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new ValidationException(message); }
    private record Key(BusinessOperation operation, String identity) { }
}
