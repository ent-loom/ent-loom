package com.entloom.crud.starter.config;

import com.entloom.declaration.EntityDeclarations;

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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 将配置冻结为现有 Core 声明；与程序化来源统一校验，不建立第二条执行链。 */
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
        this(config, entities, identities, definitions, new CrudEntityDeclarations(new EntityDeclarations()));
    }

    public ConfiguredAccessPolicies(CrudAccessProperties config, EntityMetaRegistry entities,
        BusinessIdentityRegistry identities, ConfiguredBusinessIdentities definitions, CrudEntityDeclarations entityFiles) {
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
        Map<String, CrudEntityDeclarations.EntityOptions> declarations = entityFiles.options();
        Map<String, EntityPolicyBinding> fileBindings = entityFiles.bindings(entities);
        // 不按资源名隐式绑定策略。
        declarations.forEach((resource, declaration) -> {
            require(metas.containsKey(resource), "实体差异引用未知实体: " + resource);
            require(entityFiles.fields(resource).isEmpty() || fileBindings.containsKey(resource),
                "字段授权实体未选择策略: " + resource);
            require(declaration.getOperations() == null || fileBindings.containsKey(resource),
                "操作差异的实体未选择策略: " + resource);
        });
        Set<String> configuredResources = new LinkedHashSet<>();
        // 实体文件编译为既有策略绑定模型。
        for (var selected : fileBindings.entrySet()) {
            String resource = selected.getKey();
            EntityMeta meta = metas.get(resource);
            require(meta != null, "策略绑定未知实体: " + resource);
            var declaration = declarations.getOrDefault(resource, new CrudEntityDeclarations.EntityOptions());
            for (var relation : declaration.getRelations().entrySet()) {
                require(!config.getPolicies().containsKey(selected.getValue().getTemplateCode()) || config.getRelations().containsKey(relation.getKey()),
                    "实体引用未知关系: " + resource + "/" + relation.getKey());
                var field = meta.resolveFieldMeta(relation.getValue());
                require(field != null && !field.isRelation(),
                    "实体关系字段必须是已有标量属性: " + resource + "/" + relation.getValue());
            }
            EntityPolicyBinding binding = selected.getValue();
            String policy = binding.getTemplateCode();
            if (!config.getPolicies().containsKey(policy)) {
                require(declaration.getOperations() == null && entityFiles.fields(resource).isEmpty(),
                    "Java 策略的操作目录与字段授权须由项目声明: " + resource);
                bindings.add(binding);
                continue;
            }
            String declarationSource = entityFiles.source(resource);
            var branches = config.getPolicies().get(policy);
            require(branches != null, declarationSource + ": 实体绑定未知策略: " + resource + " -> " + policy);
            for (String identity : entityFiles.fields(resource).keySet()) {
                require(branches.containsKey(identity), declarationSource + ": 字段授权引用未使用的身份: " + identity);
            }
            Map<String, List<String>> operationCatalog = resolveOperations(declaration, branches, declarationSource + "/" + resource);
            String operationSource = declaration.getOperations() == null
                ? SOURCE + ".policies." + policy : entityFiles.source(resource, "operations");
            configuredResources.add(resource);
            Map<String, String> relations = new LinkedHashMap<>();
            Map<String, String> relationDeclarations = new LinkedHashMap<>(declaration.getRelations());
            for (var branch : branches.entrySet()) {
                String identity = branch.getKey();
                String role = defaultName(branch.getValue().getScope(), identity);
                var relation = config.getRelations().get(role);
                String field = relationDeclarations.getOrDefault(role, defaultName(relation.getField(), role + "Id"));
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
                AuthorizationFields fieldPlan = compileFields(meta, entities, resource, identity, entityFiles);
                for (String action : branch.getValue().getActions()) {
                    require(operationCatalog.containsKey(action), "策略动作未在实体操作目录注册: " + resource + "/" + action);
                    Key key = new Key(new BusinessOperation(resource, action), identity);
                    fields.put(key, fieldPlan);
                    identityReaders.put(key, reader);
                }
            }
            String bindingSource = binding.getSource();
            bindings.add(new EntityPolicyBinding(meta.getEntityType(), policy, bindingSource, relations));
            for (var action : operationCatalog.entrySet()) {
                BusinessOperation operation = new BusinessOperation(resource, name(action.getKey(), "动作"));
                operations.add(new BusinessOperationDeclaration(operation, operationSource));
                Set<String> seen = new LinkedHashSet<>();
                for (String shape : action.getValue()) {
                    require(seen.add(shape), "重复 CRUD 映射: " + operation + "/" + shape);
                    mappings.add(new CrudOperationMapping(operationKey(shape, operationSource), null, operation, operationSource));
                }
            }
        }
        require(config.getPolicies().isEmpty() || !configuredResources.isEmpty(), "策略配置没有实体消费者");
    }

    public List<EntityPolicyTemplate> templates() { return List.copyOf(templates); }
    public List<EntityPolicyBinding> bindings() { return List.copyOf(bindings); }
    public List<BusinessOperationDeclaration> operations() { return List.copyOf(operations); }
    public List<CrudOperationMapping> mappings() { return List.copyOf(mappings); }

    /** 按动作整体替换；未声明的标准动作沿用缺省，不隐式开放批量操作。 */
    private static Map<String, List<String>> resolveOperations(CrudEntityDeclarations.EntityOptions entity,
        Map<String, CrudAccessProperties.Branch> branches, String resource) {
        Map<String, List<String>> overrides = entity.getOperations() == null ? Map.of() : entity.getOperations();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (CrudAccessProperties.Branch branch : branches.values()) {
            for (String action : branch.getActions()) {
                List<String> shapes = overrides.containsKey(action) ? overrides.get(action) : DEFAULT_OPERATIONS.get(action);
                require(shapes != null, "业务动作须显式声明 operations: " + resource + "/" + action);
                result.putIfAbsent(action, shapes);
            }
        }
        overrides.forEach((action, shapes) -> {
            require(shapes != null, "操作映射不能为 null: " + resource + "/operations/" + action);
            result.put(action, shapes);
        });
        return result;
    }

    /** 字段差异归实体；同一模板可绑定不同结构的实体。 */
    private static AuthorizationFields compileFields(
        EntityMeta meta, EntityMetaRegistry entities, String resource, String identity, CrudEntityDeclarations entityFiles) {
        var limit = EntityPolicyRegistry.compileFieldLimit(meta, entities);
        Map<AuthorizationFields.Use, Set<String>> compiled = new EnumMap<>(AuthorizationFields.Use.class);
        Map<AuthorizationFields.Use, CrudEntityDeclarations.FieldPlan> plans = new LinkedHashMap<>(
            entityFiles.fields(resource).getOrDefault(identity, Map.of()));
        for (var entry : plans.entrySet()) {
            var use = entry.getKey();
            var declaration = entry.getValue();
            String path = declaration.source();
            Set<String> candidates = new LinkedHashSet<>(limit.snapshot().get(use));
            if (use == AuthorizationFields.Use.RETURN) {
                candidates.removeAll(limit.snapshot().get(AuthorizationFields.Use.EXPAND));
            }
            switch (declaration.mode()) {
                case INCLUDE, EXCLUDE -> {
                    List<String> names = declaration.fields().isEmpty() && declaration.mode() == CrudEntityDeclarations.FieldMode.EXCLUDE
                        ? List.of() : unique(declaration.fields(), "字段用途 " + path);
                    for (String field : names) {
                        require(meta.getAllowedFields().contains(field)
                            || use == AuthorizationFields.Use.EXPAND && limit.allows(use, field),
                            "字段用途引用未知字段: " + path + "/" + field);
                    }
                    if (declaration.mode() == CrudEntityDeclarations.FieldMode.INCLUDE) candidates.retainAll(names);
                    else if (use == AuthorizationFields.Use.EXPAND) excludeRelationFields(candidates, names);
                    else candidates.removeAll(names);
                }
                case ALL, NONE -> {
                    require(declaration.fields().isEmpty(), "ALL/NONE 不得声明 fields: " + path);
                    if (declaration.mode() == CrudEntityDeclarations.FieldMode.NONE) candidates.clear();
                }
            }
            compiled.put(use, candidates);
        }
        return AuthorizationFields.of(compiled);
    }

    /** 关系名代表整段授权；先转为已知投影，子字段排除才不会被整段授权绕过。 */
    private static void excludeRelationFields(Set<String> candidates, List<String> excluded) {
        Set<String> paths = new LinkedHashSet<>(candidates);
        candidates.removeIf(field -> paths.stream().anyMatch(path -> path.startsWith(field + ".")));
        candidates.removeIf(field -> excluded.stream().anyMatch(name ->
            field.equals(name) || field.startsWith(name + ".")));
    }

    /** YAML 授权显式动作与实体字段方案（含 RETURN/FILTER/SORT 缺省）；项目动态判断只能进一步收窄或拒绝。 */
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

    private static CrudOperationKey operationKey(String shape, String source) {
        QueryOperation query = QueryOperation.from(shape);
        if (query != null) return CrudOperationKey.of(query);
        CommandOperation command = CommandOperation.from(shape);
        require(command != null && command != CommandOperation.ACTION, source + ": 不支持的声明式 CRUD 映射: " + shape);
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
