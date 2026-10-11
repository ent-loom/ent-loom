package com.entloom.crud.core.runtime.model;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 全局归属字段候选与实体例外，在运行时模型冻结前统一落实保护；不生成访问授权。 */
public final class CrudScopeFieldRules {
    private final Set<String> defaults;
    private final Map<String, Set<String>> exclusions;

    public CrudScopeFieldRules() {
        this(Collections.<String>emptyList(), Collections.<String, Collection<String>>emptyMap());
    }

    public CrudScopeFieldRules(Collection<String> defaults, Map<String, ? extends Collection<String>> exclusions) {
        this.defaults = names(defaults, "全局归属字段");
        Map<String, Set<String>> frozen = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, ? extends Collection<String>> entry : exclusions.entrySet()) {
            frozen.put(name(entry.getKey()), names(entry.getValue(), "实体排除字段"));
        }
        this.exclusions = Collections.unmodifiableMap(frozen);
    }

    public CrudRuntimeModel apply(CrudRuntimeModel model) {
        Set<String> resources = new LinkedHashSet<String>();
        List<CrudRuntimeEntityModel> entities = new ArrayList<CrudRuntimeEntityModel>();
        for (CrudRuntimeEntityModel entity : model.getEntities().values()) {
            String resource = entity.getResourceDescriptor().getResourceCode();
            resources.add(resource);
            Set<String> explicit = new LinkedHashSet<String>();
            for (CrudRuntimeFieldModel field : entity.getFields().values()) {
                if (field.isScopeField()) explicit.add(field.getFieldName());
            }
            Set<String> excluded = new LinkedHashSet<String>(exclusions.getOrDefault(resource, Collections.<String>emptySet()));
            EntCrudEntity annotation = entity.getEntityType().getAnnotation(EntCrudEntity.class);
            if (annotation != null) {
                explicit.addAll(names(Arrays.asList(annotation.scopeFields()), "注解归属字段"));
                excluded.addAll(names(Arrays.asList(annotation.excludeScopeFields()), "注解排除字段"));
            }
            validateFields(entity, explicit);
            validateFields(entity, excluded);
            for (String field : excluded) {
                require(!explicit.contains(field), "归属字段同时显式登记和排除: " + resource + "/" + field);
            }
            Set<String> scopes = new LinkedHashSet<String>(defaults);
            scopes.retainAll(entity.getFields().keySet());
            scopes.removeAll(excluded);
            scopes.addAll(explicit);
            Map<String, CrudRuntimeFieldModel> fields = new LinkedHashMap<String, CrudRuntimeFieldModel>();
            for (CrudRuntimeFieldModel field : entity.getFields().values()) {
                boolean scoped = scopes.contains(field.getFieldName());
                require(!scoped || !field.isRelation(), "归属字段必须是持久化标量属性: " + resource + "/" + field.getFieldName());
                require(!scoped || entity.getIdentity() == null || !field.getFieldName().equals(entity.getIdentity().getIdField()),
                    "实体主键不能作为归属字段: " + resource + "/" + field.getFieldName());
                fields.put(field.getFieldName(), field.withScopeField(scoped));
            }
            entities.add(new CrudRuntimeEntityModel(entity.getEntityType(), entity.getResourceDescriptor(), entity.getTable(),
                entity.getIdentity(), entity.getLogicDeleteField(), entity.getLogicDeleteNotDeletedValue(),
                entity.getLogicDeleteDeletedValue(), fields, entity.getCapabilities()));
        }
        for (String resource : exclusions.keySet()) {
            require(resources.contains(resource), "归属字段排除引用未知实体资源: " + resource);
        }
        return new CrudRuntimeModel(entities, model.getRelations());
    }

    private static void validateFields(CrudRuntimeEntityModel entity, Set<String> fields) {
        for (String field : fields) require(entity.getFields().containsKey(field),
            "归属字段声明引用未知持久化字段: " + entity.getResourceDescriptor().getResourceCode() + "/" + field);
    }

    private static Set<String> names(Collection<String> values, String label) {
        require(values != null, label + " 不能为空");
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) require(result.add(name(value)), label + " 重复: " + value);
        return Collections.unmodifiableSet(result);
    }

    private static String name(String value) {
        require(value != null && !value.trim().isEmpty() && value.equals(value.trim()), "归属字段或资源名称不能为空或含首尾空格");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }
}
