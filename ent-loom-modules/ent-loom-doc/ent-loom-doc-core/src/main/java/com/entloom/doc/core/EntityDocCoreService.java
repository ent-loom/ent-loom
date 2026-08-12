package com.entloom.doc.core;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldConstraintModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocIndexModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.doc.core.parser.DocNativeAnnotationParser;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocIndexProvider;
import com.entloom.doc.core.spi.DocResourceDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体文档核心组装服务（不依赖 Spring / Web / 业务模块）。
 */
public class EntityDocCoreService {

    private final DocNativeAnnotationParser nativeParser;

    public EntityDocCoreService(DocEntityMetaResolver entityMetaResolver, DocIndexProvider indexProvider) {
        if (entityMetaResolver == null) {
            throw new IllegalArgumentException("entityMetaResolver 不能为空");
        }
        this.nativeParser = new DocNativeAnnotationParser(entityMetaResolver, indexProvider);
    }

    public Map<String, Object> buildOne(List<? extends DocResourceDefinition> definitions, String resourceCode) {
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        String normalizedCode = trimToNull(resourceCode);
        if (normalizedCode == null) {
            return null;
        }
        for (DocResourceDefinition definition : definitions) {
            if (definition == null || !normalizedCode.equals(trimToEmpty(definition.getResourceCode()))) {
                continue;
            }
            return buildOne(buildNativeModel(definition));
        }
        return null;
    }

    public List<Map<String, Object>> buildAll(List<? extends DocResourceDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocResourceDefinition definition : definitions) {
            Map<String, Object> doc = buildOne(buildNativeModel(definition));
            if (doc != null) {
                items.add(doc);
            }
        }
        return items;
    }

    public Map<String, Object> buildOne(DocEntityModel model) {
        if (model == null || model.entityClass() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(model.hidden().value())) {
            return null;
        }
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("resourceCode", valueOrDefault(model.resourceCode().value(), ""));
        item.put("entityClass", model.entityClass().getName());
        item.put("entityName", valueOrDefault(model.entityName().value(), valueOrDefault(model.resourceCode().value(), model.entityClass().getSimpleName())));
        item.put("description", valueOrDefault(model.description().value(), ""));
        item.put("tableName", valueOrDefault(model.tableName().value(), ""));
        item.put("group", valueOrDefault(model.group().value(), ""));
        item.put("remark", valueOrDefault(model.remark().value(), ""));
        item.put("hidden", Boolean.TRUE.equals(model.hidden().value()));
        item.put("visibleFor", model.visibleFor());
        item.put("fields", buildFieldDocs(model));
        List<Map<String, Object>> relations = buildRelationDocs(model.relations());
        item.put("relations", relations);
        List<Map<String, Object>> indexes = buildIndexDocs(model.indexes());
        item.put("indexes", indexes);
        item.put("uniqueIndexes", filterIndexByUnique(indexes, true));
        item.put("normalIndexes", filterIndexByUnique(indexes, false));
        return item;
    }

    public List<Map<String, Object>> buildAllModels(List<DocEntityModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocEntityModel model : models) {
            Map<String, Object> doc = buildOne(model);
            if (doc != null) {
                items.add(doc);
            }
        }
        return items;
    }

    private DocEntityModel buildNativeModel(DocResourceDefinition definition) {
        if (definition == null || definition.getEntityClass() == null) {
            return null;
        }
        return nativeParser.parseWithDiagnostics(definition.getEntityClass(), definition.getResourceCode(), definition.getTableName()).value();
    }

    private List<Map<String, Object>> buildFieldDocs(DocEntityModel model) {
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        Map<String, DocRelationModel> relationByField = relationByField(model.relations());
        for (DocFieldModel field : model.fields()) {
            if (field == null) {
                continue;
            }
            if (Boolean.TRUE.equals(field.hidden().value())) {
                continue;
            }
            DocRelationModel relation = relationByField.get(field.property());
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("property", field.property());
            item.put("column", valueOrDefault(field.column().value(), ""));
            item.put("persistent", !isBlank(field.column().value()));
            item.put("javaType", field.javaType() == null ? "" : field.javaType().getSimpleName());
            item.put("name", valueOrDefault(field.name().value(), field.property()));
            item.put("description", valueOrDefault(field.description().value(), ""));
            item.put("example", valueOrDefault(field.example().value(), ""));
            item.put("examples", field.examples());
            item.put("required", Boolean.TRUE.equals(field.required().value()));
            item.put("readOnly", Boolean.TRUE.equals(field.readOnly().value()));
            item.put("maxLength", field.maxLength().value() == null ? Integer.valueOf(-1) : field.maxLength().value());
            item.put("minLength", field.minLength().value() == null ? Integer.valueOf(-1) : field.minLength().value());
            item.put("fieldKind", valueOrDefault(field.fieldKind().value(), ""));
            item.put("role", valueOrDefault(field.role().value(), ""));
            item.put("createDefaultValue", valueOrDefault(field.createDefaultValue().value(), ""));
            item.put("group", valueOrDefault(field.group().value(), ""));
            item.put("remark", valueOrDefault(field.remark().value(), ""));
            item.put("hidden", Boolean.TRUE.equals(field.hidden().value()));
            item.put("visibleFor", field.visibleFor());
            item.put("constraints", buildConstraints(field.constraints()));
            putRelationFields(item, relation);
            item.put("enumItems", buildEnumItems(field.javaType()));
            fields.add(item);
        }
        return fields;
    }

    private List<Map<String, Object>> buildRelationDocs(List<DocRelationModel> relations) {
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocRelationModel relation : relations) {
            if (relation == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("relationField", valueOrDefault(relation.relationField(), ""));
            item.put("targetService", valueOrDefault(relation.targetService().value(), ""));
            item.put("targetEntity", valueOrDefault(relation.targetEntity().value(), ""));
            item.put("sourceField", valueOrDefault(relation.sourceField().value(), ""));
            item.put("targetField", valueOrDefault(relation.targetField().value(), ""));
            item.put("cardinality", relation.cardinality().value() == null ? "" : relation.cardinality().value().name());
            item.put("ownerSide", relation.ownerSide().value() == null ? "" : relation.ownerSide().value().name());
            item.put("resolutionStatus", relation.resolutionStatus().value() == null ? "" : relation.resolutionStatus().value().name());
            item.put("targetEntityLabel", valueOrDefault(relation.targetEntityLabel().value(), ""));
            item.put("relationRemark", valueOrDefault(relation.relationRemark().value(), ""));
            item.put("sourceFieldInferred", relation.sourceFieldInferred());
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> buildIndexDocs(List<DocIndexModel> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocIndexModel index : indexes) {
            if (index == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", valueOrDefault(index.name().value(), ""));
            item.put("fields", index.fields());
            item.put("unique", Boolean.TRUE.equals(index.unique().value()));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> buildConstraints(List<DocFieldConstraintModel> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (DocFieldConstraintModel constraint : constraints) {
            if (constraint == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", valueOrDefault(constraint.name(), ""));
            item.put("value", valueOrDefault(constraint.value(), ""));
            items.add(item);
        }
        return items;
    }

    private void putRelationFields(LinkedHashMap<String, Object> item, DocRelationModel relation) {
        item.put("targetService", relation == null ? "" : valueOrDefault(relation.targetService().value(), ""));
        item.put("targetEntity", relation == null ? "" : valueOrDefault(relation.targetEntity().value(), ""));
        item.put("sourceField", relation == null ? "" : valueOrDefault(relation.sourceField().value(), ""));
        item.put("targetField", relation == null ? "" : valueOrDefault(relation.targetField().value(), ""));
        item.put("cardinality", relation == null || relation.cardinality().value() == null ? "" : relation.cardinality().value().name());
        item.put("ownerSide", relation == null || relation.ownerSide().value() == null ? "" : relation.ownerSide().value().name());
        item.put("resolutionStatus", relation == null || relation.resolutionStatus().value() == null ? "" : relation.resolutionStatus().value().name());
        item.put("targetEntityLabel", relation == null ? "" : valueOrDefault(relation.targetEntityLabel().value(), ""));
        item.put("relationRemark", relation == null ? "" : valueOrDefault(relation.relationRemark().value(), ""));
        item.put("sourceFieldInferred", relation != null && relation.sourceFieldInferred());
    }

    private Map<String, DocRelationModel> relationByField(List<DocRelationModel> relations) {
        LinkedHashMap<String, DocRelationModel> result = new LinkedHashMap<String, DocRelationModel>();
        if (relations == null) {
            return result;
        }
        for (DocRelationModel relation : relations) {
            if (relation != null && !isBlank(relation.relationField())) {
                result.put(relation.relationField(), relation);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildEnumItems(Class<?> javaType) {
        if (javaType == null || !javaType.isEnum()) {
            return Collections.emptyList();
        }
        Object[] constants = javaType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> enumItems = new ArrayList<Map<String, Object>>();
        for (Object constant : constants) {
            if (!(constant instanceof Enum)) {
                continue;
            }
            Enum<?> enumConstant = (Enum<?>) constant;
            LinkedHashMap<String, Object> enumItem = new LinkedHashMap<String, Object>();
            enumItem.put("name", enumConstant.name());
            enumItem.put("label", resolveEnumDesc(javaType, constant));
            enumItems.add(enumItem);
        }
        return enumItems;
    }

    private String resolveEnumDesc(Class<?> enumType, Object enumConstant) {
        String desc = readStringByGetter(enumType, enumConstant, "getLabel");
        if (!isBlank(desc)) {
            return desc;
        }
        desc = readStringByField(enumType, enumConstant, "label");
        if (!isBlank(desc)) {
            return desc;
        }
        desc = readStringByGetter(enumType, enumConstant, "getDesc");
        if (!isBlank(desc)) {
            return desc;
        }
        desc = readStringByField(enumType, enumConstant, "desc");
        if (!isBlank(desc)) {
            return desc;
        }
        desc = readStringByGetter(enumType, enumConstant, "getDescription");
        if (!isBlank(desc)) {
            return desc;
        }
        desc = readStringByField(enumType, enumConstant, "description");
        if (!isBlank(desc)) {
            return desc;
        }
        return "";
    }

    private String readStringByGetter(Class<?> type, Object target, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignore) {
            return "";
        }
    }

    private String readStringByField(Class<?> type, Object target, String fieldName) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignore) {
            return "";
        }
    }

    private List<Map<String, Object>> filterIndexByUnique(List<Map<String, Object>> indexes, boolean unique) {
        if (indexes == null || indexes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> index : indexes) {
            if (index != null && Boolean.valueOf(unique).equals(toBoolean(index.get("unique")))) {
                result.add(index);
            }
        }
        return result;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return Boolean.valueOf(((Number) value).intValue() != 0);
        }
        String text = trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
