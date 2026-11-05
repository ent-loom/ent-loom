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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.Collection;
import java.util.Collections;
import java.math.BigDecimal;
import java.math.BigInteger;

/** 将配置冻结为现有 Core 声明；与程序化来源统一校验，不建立第二条执行链。 */
public final class ConfiguredAccessPolicies {
    private static final String SOURCE = "ent.loom.crud.access";
    private static final String PUBLIC_BRANCH = "public";
    private static final String ALL_SCOPE = "all";
    /** 标准动作独立映射；业务动作只绑定同名显式 Handler。 */
    private static final Map<String, List<String>> DEFAULT_OPERATIONS = Map.of(
        "view", List.of("LIST", "PAGE", "FIND_ONE", "DETAIL"),
        "create", List.of("CREATE"),
        "update", List.of("UPDATE"),
        "delete", List.of("DELETE"),
        "batch-create", List.of("CREATE_BATCH"),
        "batch-update", List.of("UPDATE_BATCH"),
        "batch-delete", List.of("DELETE_BATCH"));
    private static final Set<String> STANDARD_ACTIONS = Set.of("view", "stats", "export", "create", "update", "delete",
        "batch-create", "batch-update", "batch-delete");
    private final Map<String, EffectiveAccess> effective = new LinkedHashMap<>();
    private final Map<Key, AuthorizationScope> configuredScopes = new LinkedHashMap<>();

    /** 启动期冻结的有效配置；仅供受控诊断，不发布公共 HTTP 接口。 */
    public record EffectiveAccess(String policy, Set<String> actions, Map<String, Set<String>> grants,
        Map<String, String> scopes, Map<String, Map<String, Object>> rules, Map<String, String> sources,
        Map<String, Map<AuthorizationFields.Use, Set<String>>> fields) { }

    public Map<String, EffectiveAccess> snapshot() { return Collections.unmodifiableMap(effective); }
    private final List<EntityPolicyTemplate> templates = new ArrayList<>();
    private final List<EntityPolicyBinding> bindings = new ArrayList<>();
    private final List<BusinessOperationDeclaration> operations = new ArrayList<>();
    private final List<CrudOperationMapping> mappings = new ArrayList<>();
    private final Map<Key, AuthorizationFields> fields = new LinkedHashMap<>();
    private final Map<Key, Function<BusinessIdentity, Object>> identityReaders = new LinkedHashMap<>();

    public ConfiguredAccessPolicies(CrudAccessProperties config, EntityMetaRegistry entities,
        BusinessIdentityRegistry identities, ConfiguredBusinessIdentities definitions, CrudEntityDeclarations entityFiles,
        Set<Class<?>> javaBoundEntities) {
        entityFiles.validate(entities);
        require(!identities.snapshot().containsKey(PUBLIC_BRANCH), "public 为公共分支保留名称，不能注册为身份");
        require(config.getDefaults().getActions() == null, SOURCE + ".defaults 不支持 actions，请在策略中声明");
        require(!config.getRelations().containsKey(ALL_SCOPE), SOURCE + ".relations: all 为全量范围保留名称");
        Set<String> knownActions = new LinkedHashSet<>(STANDARD_ACTIONS);
        entityFiles.actions().values().forEach(knownActions::addAll);
        validateLayer(config.getDefaults(), identities, knownActions, SOURCE + ".defaults");
        validateScopes(config.getDefaults(), config, SOURCE + ".defaults");
        config.getPolicies().forEach((code, policy) -> {
            name(code, "策略");
            require(policy.getActions() != null, SOURCE + ".policies." + code + ": 必须声明 actions");
            validateLayer(policy, identities, knownActions, SOURCE + ".policies." + code);
            validateScopes(policy, config, SOURCE + ".policies." + code);
        });
        if (config.getDefaultPolicy() != null) require(config.getPolicies().containsKey(config.getDefaultPolicy()),
            SOURCE + ".default-policy 引用未知 YAML 策略: " + config.getDefaultPolicy());
        for (EntityMeta meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            var declaration = entityFiles.options().getOrDefault(resource, new CrudEntityDeclarations.EntityOptions());
            String selected = declaration.getPolicy();
            if (selected == null && javaBoundEntities.contains(meta.getEntityType())) continue;
            // 仅已发布或已有实体差异的资源应用缺省；内部未声明资源不会自动发布。
            if (selected == null && entityFiles.httpExposed(resource)) selected = config.getDefaultPolicy();
            if (selected == null) {
                require(declaration.getActionSelection() == null && declaration.getAccess().getAccess().isEmpty() && declaration.getOperations() == null,
                    entityFiles.source(resource) + ": 实体访问差异缺少有效策略，请选择 access.policy 或为已发布实体配置 default-policy");
                continue;
            }
            String source = declaration.getPolicy() == null ? SOURCE + ".default-policy [实体=" + resource + "]"
                : entityFiles.source(resource, "access.policy");
            CrudAccessProperties.Policy policy = config.getPolicies().get(selected);
            if (policy == null) {
                require(declaration.getActionSelection() == null && declaration.getAccess().getAccess().isEmpty() && declaration.getOperations() == null,
                    source + ": Java 策略的操作、字段与授权由项目声明");
                bindings.add(new EntityPolicyBinding(meta.getEntityType(), selected, source, declaration.getRelations()));
                continue;
            }
            String entitySource = entityFiles.source(resource);
            validateLayer(declaration.getAccess(), identities, knownActions, entitySource);
            validateScopes(declaration.getAccess(), config, entitySource);
            String policyActionsSource = SOURCE + ".policies." + selected + ".actions";
            String entityActionsSource = entityFiles.source(resource, "access.actions");
            Set<String> finalActions = resolveActions(declaration.getActionSelection(), policy.getActions(),
                knownActions, entityActionsSource);
            Map<String, Set<String>> grants = new LinkedHashMap<>();
            Map<String, String> scopes = new LinkedHashMap<>();
            Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
            Map<String, Map<AuthorizationFields.Use, CrudEntityDeclarations.FieldPlan>> fieldPlans = new LinkedHashMap<>();
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("policy", source);
            sources.put("actions", declaration.getActionSelection() == null
                ? policyActionsSource : entityActionsSource);
            if (declaration.getActionSelection() != null && declaration.getActionSelection().replacement() == null)
                sources.put("actions.inherited", policyActionsSource);
            merge(config.getDefaults(), grants, scopes, rules, fieldPlans, sources, finalActions, false, SOURCE + ".defaults");
            merge(policy, grants, scopes, rules, fieldPlans, sources, finalActions, false, SOURCE + ".policies." + selected);
            merge(declaration.getAccess(), grants, scopes, rules, fieldPlans, sources, finalActions, true, entitySource);
            Map<String, List<String>> operationCatalog = resolveOperations(declaration, finalActions, entitySource);
            List<EntityPolicyTemplate.Rule> policyRules = new ArrayList<>();
            Map<String, String> relationFields = new LinkedHashMap<>();
            Map<String, Map<AuthorizationFields.Use, Set<String>>> fieldSnapshot = new LinkedHashMap<>();
            for (var branch : grants.entrySet()) {
                String identity = branch.getKey();
                if (branch.getValue().isEmpty()) continue;
                String path = entitySource + ".access." + identity + ".scope";
                String scope = scopes.get(identity);
                Map<String, Object> ruleConfig = rules.get(identity);
                require(scope != null || ruleConfig != null, path + ": 已授权身份缺少可用范围，请显式声明 all、关系或 rules");
                boolean publicBranch = PUBLIC_BRANCH.equals(identity);
                if (publicBranch) {
                    var publicAccess = declaration.getAccess().getAccess().get(identity);
                    require(publicAccess != null && (publicAccess.getScope() != null || publicAccess.getRules() != null),
                        path + ": 公共范围须在实体显式声明");
                    var returns = publicAccess.getFields().get(AuthorizationFields.Use.RETURN);
                    require(returns != null && returns.getMode() == CrudEntityDeclarations.FieldMode.INCLUDE,
                        entitySource + ".access.public.fields.RETURN: 公共返回字段必须显式声明 INCLUDE 白名单");
                }
                Function<BusinessIdentity, Object> identityReader = null;
                String role = null;
                if (scope != null && !ALL_SCOPE.equals(scope)) {
                    require(!publicBranch, path + ": 公共分支不能引用身份关系范围: " + scope);
                    var relation = config.getRelations().get(scope);
                    require(relation != null, path + ": 未知关系: " + scope);
                    role = scope;
                    String field = declaration.getRelations().getOrDefault(role, defaultName(relation.getField(), role + "Id"));
                    var entityField = meta.resolveFieldMeta(field);
                    require(entityField != null && !entityField.isRelation(), path + ": 关系字段必须是已有标量属性: " + field);
                    relationFields.put(role, field);
                    var definition = definitions.definitions().get(identity);
                    String identityField = defaultName(relation.getIdentityField(), definition == null ? role + "Id" : definition.getIdField());
                    if (definition != null) {
                        require(definition.getIdField().equals(identityField), path + ": 关系引用未知身份属性: " + identityField);
                        require(definition.getIdType().getJavaType() == org.springframework.util.ClassUtils.resolvePrimitiveIfNecessary(entityField.getJavaType()),
                            path + ": 身份 ID 类型与实体关系字段不匹配");
                        identityReader = snapshot -> {
                            require(snapshot instanceof ConfiguredBusinessIdentity
                                && definition.equals(((ConfiguredBusinessIdentity) snapshot).getDefinition()), "当前身份结构与定义不匹配: " + identity);
                            return ((ConfiguredBusinessIdentity) snapshot).read(identityField);
                        };
                    } else identityReader = reader(identities.snapshot().get(identity).getIdentityType(), identityField);
                }
                AuthorizationFields fieldPlan = compileFields(meta, entities, resource, identity, fieldPlans.getOrDefault(identity, Map.of()));
                fieldSnapshot.put(identity, fieldPlan.resolveDefaults(EntityPolicyRegistry.compileFieldLimit(meta, entities)).snapshot());
                for (String action : branch.getValue()) {
                    policyRules.add(new EntityPolicyTemplate.Rule(action, publicBranch ? PolicyIdentityBranch.publicBranch()
                        : PolicyIdentityBranch.identity(identity), role == null ? List.of() : List.of(role)));
                    Key key = new Key(new BusinessOperation(resource, action), publicBranch ? null : identity);
                    fields.put(key, fieldPlan);
                    if (identityReader != null) identityReaders.put(key, identityReader);
                    AuthorizationScope staticScope = compileRule(meta, ruleConfig, entitySource + ".access." + identity + ".rules");
                    if (staticScope != null) configuredScopes.put(key, staticScope);
                }
            }
            for (String identity : fieldPlans.keySet()) require(grants.containsKey(identity),
                entitySource + ".access." + identity + ".fields: 字段授权引用未使用的身份: " + identity);
            for (String relation : declaration.getRelations().keySet()) require(relationFields.containsKey(relation),
                entitySource + ".relations: 实体引用未知或未使用关系: " + relation);
            // 实体差异在配置边界折叠为普通 Core 模板，运行时只保留一条授权链。
            String compiledCode = "configured/" + resource;
            templates.add(new EntityPolicyTemplate(compiledCode, source, policyRules));
            bindings.add(new EntityPolicyBinding(meta.getEntityType(), compiledCode, source, relationFields));
            for (var action : operationCatalog.entrySet()) {
                BusinessOperation operation = new BusinessOperation(resource, action.getKey());
                operations.add(new BusinessOperationDeclaration(operation, entitySource));
                for (String shape : action.getValue()) mappings.add(new CrudOperationMapping(operationKey(shape, entitySource), null, operation, entitySource));
                if (entityFiles.actions().getOrDefault(resource, Set.of()).contains(action.getKey())) {
                    require(action.getValue().isEmpty(), entitySource + ": 动作不能同时绑定业务 Handler 与通用 CRUD: " + action.getKey());
                    mappings.add(new CrudOperationMapping(CrudOperationKey.of(CommandOperation.ACTION), action.getKey(), operation, entitySource));
                } else require(!action.getValue().isEmpty(), entitySource + ": 缺失业务 Handler 声明或执行入口: " + action.getKey());
            }
            Map<String, Set<String>> immutableGrants = new LinkedHashMap<>();
            grants.forEach((identity, values) -> immutableGrants.put(identity, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
            effective.put(resource, new EffectiveAccess(selected, Collections.unmodifiableSet(finalActions),
                Collections.unmodifiableMap(immutableGrants), Map.copyOf(scopes), Map.copyOf(rules), Map.copyOf(sources), Map.copyOf(fieldSnapshot)));
        }
    }

    private static Set<String> resolveActions(CrudEntityDeclarations.ActionSelection selection, List<String> inherited,
        Set<String> known, String source) {
        Set<String> result = new LinkedHashSet<>(selection == null || selection.replacement() == null
            ? inherited : selection.replacement());
        if (selection == null) return result;
        validateActionNames(selection.replacement(), known, source);
        validateActionNames(selection.add(), known, source + ".add");
        validateActionNames(selection.remove(), known, source + ".remove");
        result.addAll(selection.add());
        result.removeAll(selection.remove());
        return result;
    }

    private static void validateActionNames(List<String> actions, Set<String> known, String source) {
        if (actions == null) return;
        for (String action : checked(actions, source))
            require(known.contains(action), source + " 包含未知动作: " + action + "；actions 不支持通配符，请声明已知动作码");
    }

    private static void validateScopes(CrudAccessProperties.Policy layer, CrudAccessProperties config, String source) {
        layer.getAccess().forEach((identity, subject) -> {
            String path = source + ".access." + identity;
            String scope = subject.getScope();
            if (scope != null) require(ALL_SCOPE.equals(scope) || config.getRelations().containsKey(scope),
                path + ".scope: 未知关系: " + scope);
            var rule = subject.getRules();
            if (rule == null) return;
            require(!rule.isEmpty(), path + ".rules: 规则不能为空");
            rule.forEach((field, value) -> {
                require(field != null && !field.trim().isEmpty() && field.equals(field.trim()),
                    path + ".rules: 字段名不能为空或包含首尾空格");
                require(value != null, path + ".rules." + field + ": 值不能为空");
                if (value instanceof Collection<?>) require(!((Collection<?>) value).isEmpty(),
                    path + ".rules." + field + ": IN 值不能为空集合");
            });
        });
    }

    /** 将简单 YAML 规则编译为授权范围：标量为 EQ，列表为 IN，字段之间默认 AND。 */
    private static AuthorizationScope compileRule(EntityMeta meta, Map<String, Object> rule, String source) {
        if (rule == null || rule.isEmpty()) return null;
        List<AuthorizationScope> scopes = new ArrayList<>();
        rule.forEach((fieldName, raw) -> {
            var field = meta.resolveFieldMeta(fieldName);
            require(field != null && meta.getAllowedFields().contains(fieldName) && !field.isRelation(),
                source + ": 规则字段必须是实体已声明的标量字段: " + fieldName);
            if (raw instanceof Collection<?>) {
                List<Object> values = new ArrayList<>();
                for (Object value : (Collection<?>) raw) values.add(convertRuleValue(value, field.getJavaType(), source + "." + fieldName));
                scopes.add(AuthorizationScope.in(fieldName, values));
            } else {
                scopes.add(AuthorizationScope.equal(fieldName, convertRuleValue(raw, field.getJavaType(), source + "." + fieldName)));
            }
        });
        return scopes.size() == 1 ? scopes.get(0) : AuthorizationScope.and(scopes);
    }

    private static Object convertRuleValue(Object raw, Class<?> targetType, String source) {
        require(raw != null, source + ": 值不能为空");
        try {
            if (targetType == String.class) return raw.toString();
            if (targetType == Boolean.class || targetType == boolean.class) {
                require(raw instanceof Boolean, source + ": 必须是布尔值");
                return raw;
            }
            if (targetType.isEnum()) {
                require(raw instanceof String, source + ": 枚举值必须是字符串");
                return Enum.valueOf((Class) targetType, ((String) raw).toUpperCase(java.util.Locale.ROOT));
            }
            if (targetType == Long.class || targetType == long.class) return raw instanceof Number ? ((Number) raw).longValue() : Long.valueOf(raw.toString());
            if (targetType == Integer.class || targetType == int.class) return raw instanceof Number ? ((Number) raw).intValue() : Integer.valueOf(raw.toString());
            if (targetType == Short.class || targetType == short.class) return raw instanceof Number ? ((Number) raw).shortValue() : Short.valueOf(raw.toString());
            if (targetType == Byte.class || targetType == byte.class) return raw instanceof Number ? ((Number) raw).byteValue() : Byte.valueOf(raw.toString());
            if (targetType == BigDecimal.class) return raw instanceof BigDecimal ? raw : new BigDecimal(raw.toString());
            if (targetType == BigInteger.class) return raw instanceof BigInteger ? raw : new BigInteger(raw.toString());
            if (targetType == Double.class || targetType == double.class) return raw instanceof Number ? ((Number) raw).doubleValue() : Double.valueOf(raw.toString());
            if (targetType == Float.class || targetType == float.class) return raw instanceof Number ? ((Number) raw).floatValue() : Float.valueOf(raw.toString());
            require(false, source + ": 不支持的范围字段类型: " + targetType.getName());
        } catch (RuntimeException failure) {
            if (failure instanceof ValidationException) throw failure;
            throw new ValidationException(source + ": 值无法转换为 " + targetType.getName() + ": " + raw);
        }
        return raw;
    }

    private static void validateLayer(CrudAccessProperties.Policy layer, BusinessIdentityRegistry identities,
        Set<String> known, String source) {
        if (layer.getActions() != null) for (String action : checked(layer.getActions(), source + ".actions"))
            require(known.contains(action), source + ".actions 包含未知动作: " + action);
        layer.getAccess().forEach((identity, subject) -> {
            String path = source + ".access." + identity;
            require(PUBLIC_BRANCH.equals(identity) || identities.snapshot().containsKey(identity),
                path + ": 策略引用未知身份: " + identity);
            require(subject != null, path + ": 主体配置不能为空");
            require(Set.of("actions", "scope", "rules", "fields").containsAll(subject.keySet()), path + ": 仅允许 actions/scope/rules/fields");
            try {
                subject.getFields().forEach((use, field) -> {
                    String fieldPath = path + ".fields." + use;
                    require(field != null && field.getMode() != null && field.getFields() != null, fieldPath + ": 字段方案不能为空");
                    require(field.getMode() != CrudEntityDeclarations.FieldMode.INCLUDE || !field.getFields().isEmpty(),
                        fieldPath + ": INCLUDE 的 fields 不能为空");
                    require((field.getMode() != CrudEntityDeclarations.FieldMode.ALL && field.getMode() != CrudEntityDeclarations.FieldMode.NONE)
                        || field.getFields().isEmpty(), fieldPath + ": ALL/NONE 不得声明 fields");
                });
                subject.getScope();
                subject.getRules();
            } catch (IllegalArgumentException failure) {
                throw new ValidationException(path + ": " + failure.getMessage());
            }
            if (subject.getActions() == null) return;
            Grant grant = grant(subject.getActions(), path + ".actions");
            for (String action : java.util.stream.Stream.of(grant.replace(), grant.add(), grant.remove())
                .filter(java.util.Objects::nonNull).flatMap(Collection::stream).toList())
                require("*".equals(action) || known.contains(action), path + ".actions 包含未知动作: " + action);
        });
    }

    private static void merge(CrudAccessProperties.Policy layer, Map<String, Set<String>> grants,
        Map<String, String> scopes, Map<String, Map<String, Object>> rules,
        Map<String, Map<AuthorizationFields.Use, CrudEntityDeclarations.FieldPlan>> fieldPlans, Map<String, String> sources,
        Set<String> actions, boolean strict, String source) {
        layer.getAccess().forEach((identity, subject) -> {
            String path = source + ".access." + identity;
            if (subject.getActions() != null) {
                Grant change = grant(subject.getActions(), path + ".actions");
                Set<String> values = new LinkedHashSet<>(change.replace() == null ? grants.getOrDefault(identity, Set.of()) : expand(change.replace(), actions));
                values.addAll(expand(change.add(), actions));
                values.removeAll(expand(change.remove(), actions));
                if (strict) {
                    Set<String> added = new LinkedHashSet<>(change.replace() == null ? List.of() : expand(change.replace(), actions));
                    added.addAll(expand(change.add(), actions));
                    require(actions.containsAll(added), path + ".actions: 显式授权超出实体最终 actions: " + added);
                }
                values.retainAll(actions);
                grants.put(identity, values);
                sources.put("grants." + identity, path + ".actions");
            }
            if (subject.getScope() != null) {
                scopes.put(identity, name(subject.getScope(), path + ".scope"));
                sources.put("scopes." + identity, path + ".scope");
            }
            if (subject.getRules() != null) {
                rules.put(identity, Collections.unmodifiableMap(new LinkedHashMap<>(subject.getRules())));
                sources.put("rules." + identity, path + ".rules");
            }
            if (!subject.getFields().isEmpty()) {
                var plans = fieldPlans.computeIfAbsent(identity, ignored -> new EnumMap<>(AuthorizationFields.Use.class));
                subject.getFields().forEach((use, field) -> plans.put(use, new CrudEntityDeclarations.FieldPlan(
                    field.getMode(), List.copyOf(field.getFields()), path + ".fields." + use)));
            }
        });
        grants.values().forEach(values -> values.retainAll(actions));
    }

    private record Grant(List<String> replace, List<String> add, List<String> remove) { }
    private static Grant grant(Object raw, String path) {
        if (raw instanceof List<?> list) return new Grant(strings(list, path), List.of(), List.of());
        if (raw instanceof Map<?, ?> map) {
            // Spring Binder 将 Map<String,Object> 下的索引列表表示为数字键。
            if (!map.isEmpty() && map.keySet().stream().allMatch(key -> key.toString().matches("[0-9]+"))) {
                return new Grant(strings(map.entrySet().stream().sorted(java.util.Comparator.comparingInt(
                    entry -> Integer.parseInt(entry.getKey().toString()))).map(Map.Entry::getValue).toList(), path), List.of(), List.of());
            }
            require(map.keySet().stream().allMatch(key -> "add".equals(key) || "remove".equals(key)), path + ": 只允许 add/remove");
            return new Grant(null, grantList(map.get("add"), path + ".add"), grantList(map.get("remove"), path + ".remove"));
        }
        // Spring 的空列表属性可能保留为空字符串。
        if ("".equals(raw)) return new Grant(List.of(), List.of(), List.of());
        throw new ValidationException(path + ": 必须为授权列表或 add/remove 对象，不能为 null");
    }
    private static List<String> grantList(Object raw, String path) {
        if (raw == null) return List.of();
        Grant value = grant(raw, path);
        require(value.replace() != null, path + ": 必须为动作列表");
        return value.replace();
    }
    private static List<String> strings(List<?> values, String path) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            require(value instanceof String, path + ": 动作必须为字符串");
            result.add((String) value);
        }
        return checked(result, path);
    }
    private static List<String> checked(List<String> values, String path) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) require(unique.add(name(value, path)), path + ": 重复动作: " + value);
        return List.copyOf(unique);
    }
    private static Set<String> expand(List<String> values, Set<String> actions) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) if ("*".equals(value)) result.addAll(actions); else result.add(value);
        return result;
    }

    public List<EntityPolicyTemplate> templates() { return List.copyOf(templates); }
    public List<EntityPolicyBinding> bindings() { return List.copyOf(bindings); }
    public List<BusinessOperationDeclaration> operations() { return List.copyOf(operations); }
    public List<CrudOperationMapping> mappings() { return List.copyOf(mappings); }

    private static Map<String, List<String>> resolveOperations(CrudEntityDeclarations.EntityOptions entity,
        Set<String> actions, String source) {
        Map<String, List<String>> overrides = entity.getOperations() == null ? Map.of() : entity.getOperations();
        require(actions.containsAll(overrides.keySet()), source + ".operations: 映射超出实体最终 actions");
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String action : actions) {
            List<String> shapes = overrides.getOrDefault(action, DEFAULT_OPERATIONS.getOrDefault(action, List.of()));
            require(shapes != null, source + ".operations." + action + ": 不能为 null");
            if (Set.of("stats", "export", "batch-create", "batch-delete").contains(action))
                require(false, source + ".access.actions: 标准动作尚无已接入的执行能力: " + action);
            for (String shape : shapes) require(DEFAULT_OPERATIONS.getOrDefault(action, List.of()).contains(shape),
                source + ".operations." + action + ": 通用映射不能跨动作授权: " + shape);
            result.put(action, List.copyOf(shapes));
        }
        return result;
    }

    /** 字段差异归实体；EXPAND 缺省开放候选投影，仍由请求显式选择关系。 */
    private static AuthorizationFields compileFields(
        EntityMeta meta, EntityMetaRegistry entities, String resource, String identity, Map<AuthorizationFields.Use, CrudEntityDeclarations.FieldPlan> declaredPlans) {
        var limit = EntityPolicyRegistry.compileFieldLimit(meta, entities);
        Map<AuthorizationFields.Use, Set<String>> compiled = new EnumMap<>(AuthorizationFields.Use.class);
        Map<AuthorizationFields.Use, CrudEntityDeclarations.FieldPlan> plans = new LinkedHashMap<>(
            declaredPlans);
        plans.putIfAbsent(AuthorizationFields.Use.EXPAND, new CrudEntityDeclarations.FieldPlan(
            PUBLIC_BRANCH.equals(identity) ? CrudEntityDeclarations.FieldMode.NONE : CrudEntityDeclarations.FieldMode.EXCLUDE, List.of(), "字段缺省 " + resource + "/" + identity + "/EXPAND"));
        var expansion = plans.get(AuthorizationFields.Use.EXPAND);
        require(!PUBLIC_BRANCH.equals(identity) || expansion.mode() == CrudEntityDeclarations.FieldMode.NONE
            || expansion.mode() == CrudEntityDeclarations.FieldMode.INCLUDE,
            expansion.source() + ": 公共关联展开仅允许 NONE 或 INCLUDE 白名单");
        require(!PUBLIC_BRANCH.equals(identity) || expansion.mode() != CrudEntityDeclarations.FieldMode.INCLUDE
            || expansion.fields().stream().allMatch(field -> field.contains(".")),
            expansion.source() + ": 公共关联白名单须逐字段声明，如 customer.id，不能开放整个关联");
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

    /** YAML 授权显式动作与实体字段方案（含 RETURN/FILTER/SORT/EXPAND 缺省）；项目动态判断只能进一步收窄或拒绝。 */
    public BusinessPermissionEvaluator evaluator(BusinessPermissionEvaluator dynamic) {
        return (visitor, operation, rule, target) -> {
            AuthorizationFields allowed = fields.get(key(rule));
            if (allowed == null) {
                return dynamic == null ? BusinessPermissionDecision.deny() : dynamic.evaluate(visitor, operation, rule, target);
            }
            allowed = allowed.resolveDefaults(rule.getFieldLimit());
            AuthorizationScope scope = configuredScopes.getOrDefault(key(rule), AuthorizationScope.all());
            if (dynamic == null) return BusinessPermissionDecision.allow(scope, allowed);
            var decision = dynamic.evaluate(visitor, operation, rule, target);
            return decision == null || decision.getKind() != BusinessPermissionDecision.Kind.ALLOW
                ? BusinessPermissionDecision.deny()
                : BusinessPermissionDecision.allow(AuthorizationScope.intersect(scope, decision.getScope()),
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
