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

/** 关系字段缺省推导实体归属，启动期冻结写入保护；不生成访问授权或查询条件。 */
public final class CrudOwnershipRules {
    private final Map<String, Relation> relations;
    private final Map<String, Ownership> declarations;

    public CrudOwnershipRules() {
        this(Collections.<String, Relation>emptyMap(), Collections.<String, Ownership>emptyMap());
    }

    public CrudOwnershipRules(Map<String, Relation> relations, Map<String, Ownership> declarations) {
        Map<String, Relation> frozenRelations = new LinkedHashMap<String, Relation>();
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            require(entry.getValue() != null, "归属关系不能为空: " + entry.getKey());
            frozenRelations.put(name(entry.getKey()), entry.getValue());
        }
        this.relations = Collections.unmodifiableMap(frozenRelations);
        Map<String, Ownership> frozenDeclarations = new LinkedHashMap<String, Ownership>();
        for (Map.Entry<String, Ownership> entry : declarations.entrySet()) {
            String resource = name(entry.getKey());
            Ownership declaration = entry.getValue();
            require(declaration != null, "实体归属声明不能为空: " + resource);
            if (declaration.relations != null) {
                for (String relation : declaration.relations) validateRelation(relation, resource);
            }
            for (String relation : declaration.fields.keySet()) validateRelation(relation, resource);
            frozenDeclarations.put(resource, declaration);
        }
        this.declarations = Collections.unmodifiableMap(frozenDeclarations);
    }

    public CrudRuntimeModel apply(CrudRuntimeModel model) {
        Set<String> resources = new LinkedHashSet<String>();
        List<CrudRuntimeEntityModel> entities = new ArrayList<CrudRuntimeEntityModel>();
        for (CrudRuntimeEntityModel entity : model.getEntities().values()) {
            String resource = entity.getResourceDescriptor().getResourceCode();
            resources.add(resource);
            Ownership declaration = declarations.get(resource);
            Map<String, String> mappings = declaration == null
                ? Collections.<String, String>emptyMap() : declaration.fields;
            for (String field : mappings.values()) validateScalarField(entity, field);
            Set<String> scopes = new LinkedHashSet<String>();
            boolean inferred = declaration == null || declaration.relations == null;
            Collection<String> selected = inferred ? relations.keySet() : declaration.relations;
            for (String relationName : selected) {
                Relation relation = relations.get(relationName);
                String fieldName = mappings.containsKey(relationName) ? mappings.get(relationName) : relation.field;
                if (inferred && !entity.getFields().containsKey(fieldName)) continue;
                validateField(entity, fieldName);
                CrudRuntimeFieldModel field = entity.getField(fieldName);
                require(relation.idType == null || wrap(field.getJavaType()).equals(wrap(relation.idType)),
                    "归属身份 ID 类型与实体关系字段不匹配: " + relationName + " -> " + resource + "/" + fieldName);
                scopes.add(fieldName);
            }
            // 显式元数据范围是底层安全约束，ownership 仅覆盖关系推导，不能解除该约束。
            for (CrudRuntimeFieldModel field : entity.getFields().values()) {
                if (field.isScopeField()) scopes.add(field.getFieldName());
            }
            EntCrudEntity annotation = entity.getEntityType().getAnnotation(EntCrudEntity.class);
            if (annotation != null) scopes.addAll(names(Arrays.asList(annotation.scopeFields())));
            for (String field : scopes) validateField(entity, field);
            Map<String, CrudRuntimeFieldModel> fields = new LinkedHashMap<String, CrudRuntimeFieldModel>();
            for (CrudRuntimeFieldModel field : entity.getFields().values()) {
                fields.put(field.getFieldName(), field.withScopeField(scopes.contains(field.getFieldName())));
            }
            entities.add(new CrudRuntimeEntityModel(entity.getEntityType(), entity.getResourceDescriptor(), entity.getTable(),
                entity.getIdentity(), entity.getLogicDeleteField(), entity.getLogicDeleteNotDeletedValue(),
                entity.getLogicDeleteDeletedValue(), fields, entity.getCapabilities()));
        }
        for (String resource : declarations.keySet()) {
            require(resources.contains(resource), "归属声明引用未知实体资源: " + resource);
        }
        return new CrudRuntimeModel(entities, model.getRelations());
    }

    private void validateRelation(String relation, String resource) {
        require(relations.containsKey(relation), "归属声明引用未知关系: " + resource + "/" + relation);
    }

    private static void validateField(CrudRuntimeEntityModel entity, String fieldName) {
        validateScalarField(entity, fieldName);
        require(entity.getIdentity() == null || !fieldName.equals(entity.getIdentity().getIdField()),
            "实体主键不能作为归属字段: " + entity.getResourceDescriptor().getResourceCode() + "/" + fieldName);
    }

    private static void validateScalarField(CrudRuntimeEntityModel entity, String fieldName) {
        String path = entity.getResourceDescriptor().getResourceCode() + "/" + fieldName;
        CrudRuntimeFieldModel field = entity.getField(fieldName);
        require(field != null, "归属关系引用未知实体字段: " + path);
        require(!field.isRelation(), "归属字段必须是持久化标量属性: " + path);
    }

    private static Set<String> names(Collection<String> values) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) require(result.add(name(value)), "归属关系或字段重复: " + value);
        return Collections.unmodifiableSet(result);
    }

    private static String name(String value) {
        require(value != null && !value.trim().isEmpty() && value.equals(value.trim()),
            "归属关系、字段或资源名称不能为空或含首尾空格");
        return value;
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        return type;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }

    /** 可复用的关系字段及身份 ID 类型；复杂 Java 身份可不指定类型。 */
    public static final class Relation {
        private final String field;
        private final Class<?> idType;

        public Relation(String field, Class<?> idType) {
            this.field = name(field);
            this.idType = idType;
        }
    }

    /** null 表示缺省推导；空集合明确关闭推导；实体字段映射同时用于缺省和显式归属。 */
    public static final class Ownership {
        private final Set<String> relations;
        private final Map<String, String> fields;

        public Ownership(Collection<String> relations, Map<String, String> fields) {
            this.relations = relations == null ? null : names(relations);
            Map<String, String> frozen = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                frozen.put(name(entry.getKey()), name(entry.getValue()));
            }
            this.fields = Collections.unmodifiableMap(frozen);
        }
    }
}
