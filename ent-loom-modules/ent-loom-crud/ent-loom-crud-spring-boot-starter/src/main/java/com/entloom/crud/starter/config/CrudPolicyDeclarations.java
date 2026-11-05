package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将实体静态边界与 Java 策略绑定编译为唯一操作目录，不参与业务授权决策。 */
public final class CrudPolicyDeclarations {
    private static final Map<String, List<String>> DEFAULT_OPERATIONS = Map.of(
        "view", List.of("LIST", "PAGE", "FIND_ONE", "DETAIL"),
        "create", List.of("CREATE"), "update", List.of("UPDATE"), "delete", List.of("DELETE"),
        "batch-update", List.of("UPDATE_BATCH"));
    private final List<EntityPolicyBinding> bindings = new ArrayList<>();
    private final List<BusinessOperationDeclaration> operations = new ArrayList<>();
    private final List<CrudOperationMapping> mappings = new ArrayList<>();

    public CrudPolicyDeclarations(EntityMetaRegistry entities, CrudEntityDeclarations declarations,
        Collection<EntityPolicyTemplate> templates, Collection<EntityPolicyBinding> javaBindings,
        Collection<EntityPolicyBinding> grantBindings, ScenePolicyRegistry scenes) {
        declarations.validate(entities);
        Map<String, EntityPolicyTemplate> policies = new LinkedHashMap<>();
        for (var template : templates) {
            var previous = policies.putIfAbsent(template.getCode(), template);
            require(previous == null, "策略模板重复: " + template.getCode() + "，来源: "
                + (previous == null ? "" : previous.getSource()) + " / " + template.getSource());
        }
        Map<Class<?>, EntityPolicyBinding> selected = new LinkedHashMap<>();
        var allBindings = new ArrayList<>(javaBindings);
        allBindings.addAll(grantBindings);
        var grantEntities = new java.util.HashSet<Class<?>>();
        for (var binding : grantBindings) grantEntities.add(binding.getEntityType());
        for (var binding : allBindings) {
            var previous = selected.putIfAbsent(binding.getEntityType(), binding);
            require(previous == null, "实体策略绑定重复: " + binding.getEntityType().getName() + "，来源: "
                + (previous == null ? "" : previous.getSource()) + " / " + binding.getSource());
        }
        for (var meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            var entity = declarations.options().get(resource);
            if (entity == null) continue;
            String source = declarations.source(resource);
            var binding = selected.get(meta.getEntityType());
            if (entity.getPolicy() != null && !grantEntities.contains(meta.getEntityType())) {
                require(binding == null, "实体策略绑定重复，来源: " + source + ".policy / "
                    + (binding == null ? "" : binding.getSource()));
                binding = new EntityPolicyBinding(meta.getEntityType(), entity.getPolicy(), source + ".policy", Map.of());
            }
            // 仅声明持久化信息的内部实体可以不加入业务授权链。
            if (binding == null) {
                require(!declarations.httpExposed(resource), source + ": HTTP 实体必须声明 EntCrudGrant 或绑定 Java 策略");
                continue;
            }
            require(policies.containsKey(binding.getTemplateCode()), source + ": 未知 Java 策略: " + binding.getTemplateCode());
            // Java 声明也不能绕过实体动作上限；超出边界直接启动失败。
            for (var rule : policies.get(binding.getTemplateCode()).getRules()) {
                require(entity.getActions().contains(rule.getOperationCode()), source + ": Java 策略超出实体动作上限: " + rule.getOperationCode());
            }
            for (var override : binding.getOverrides()) {
                require(entity.getActions().contains(override.getOperationCode()), source + ": Java 策略差异超出实体动作上限: " + override.getOperationCode());
            }
            var limits = fieldLimits(binding, entity);
            selected.put(meta.getEntityType(), new EntityPolicyBinding(meta.getEntityType(), binding.getTemplateCode(),
                binding.getSource(), binding.getRelationFields(), binding.getOverrides(), limits));
            Map<String, List<String>> overrides = entity.getOperations() == null ? Map.of() : entity.getOperations();
            require(entity.getActions().containsAll(overrides.keySet()), source + ".operations: 映射超出实体动作上限");
            for (String action : entity.getActions()) {
                require(!action.isBlank() && action.equals(action.trim()), source + ": 动作名称不能为空或含首尾空格");
                BusinessOperation operation = new BusinessOperation(resource, action);
                operations.add(new BusinessOperationDeclaration(operation, source));
                List<String> standard = DEFAULT_OPERATIONS.get(action);
                if (standard == null) {
                    require(scenes.declaresAction(resource, action), source + ": 动作缺少已支持的执行入口: " + action);
                    require(!overrides.containsKey(action) || overrides.get(action).isEmpty(), source + ": 业务动作只能绑定同名 Handler: " + action);
                    mappings.add(new CrudOperationMapping(CrudOperationKey.of(CommandOperation.ACTION), action, operation, source));
                    continue;
                }
                List<String> shapes = overrides.getOrDefault(action, standard);
                if (scenes.declaresAction(resource, action)) {
                    require(shapes.isEmpty(), source + ": 动作不能同时绑定业务 Handler 与通用 CRUD: " + action);
                    mappings.add(new CrudOperationMapping(CrudOperationKey.of(CommandOperation.ACTION), action, operation, source));
                }
                for (String shape : shapes) {
                    require(standard.contains(shape), source + ": 通用映射不能跨动作授权: " + shape);
                    QueryOperation query = QueryOperation.from(shape);
                    CrudOperationKey key = query == null ? CrudOperationKey.of(CommandOperation.from(shape)) : CrudOperationKey.of(query);
                    mappings.add(new CrudOperationMapping(key, null, operation, source));
                }
            }
        }
        bindings.addAll(selected.values());
    }

    private static Map<AuthorizationFields.Use, ? extends Collection<String>> fieldLimits(
        EntityPolicyBinding binding, CrudEntityDeclarations.EntityOptions entity) {
        if (entity.getFieldLimits() == null) return binding.getFieldLimit() == null ? null : binding.getFieldLimit().snapshot();
        Map<AuthorizationFields.Use, Collection<String>> explicit = new EnumMap<>(AuthorizationFields.Use.class);
        for (var use : AuthorizationFields.Use.values()) explicit.put(use, entity.getFieldLimits().getOrDefault(use, List.of()));
        var limit = AuthorizationFields.of(explicit);
        return binding.getFieldLimit() == null ? limit.snapshot() : limit.intersect(binding.getFieldLimit()).snapshot();
    }

    public List<EntityPolicyBinding> bindings() { return List.copyOf(bindings); }
    public List<BusinessOperationDeclaration> operations() { return List.copyOf(operations); }
    public List<CrudOperationMapping> mappings() { return List.copyOf(mappings); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }
}
