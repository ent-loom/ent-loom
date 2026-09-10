package com.entloom.doc.core.contract;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldConstraintModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocIndexModel;
import com.entloom.doc.core.model.DocRelationModel;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 DOC Runtime Model 投影为实体文档契约 v1 的安全结构。
 *
 * <p>调用方必须在进入本类前完成实体暴露、认证和授权。本类只负责隐藏标记、
 * 物理实现字段和不完整关系的安全过滤，以及稳定的公共字段投影。</p>
 */
public final class EntityDocumentationProjector {
    /** 当前实体文档契约版本。 */
    public static final String CONTRACT_VERSION = "1.0.0";

    /**
     * 投影一组已经通过外部暴露与授权检查的 DOC 模型。
     *
     * @param models DOC Runtime Model 集合
     * @return 符合 Entity Documentation Contract v1 结构的 Map
     */
    public Map<String, Object> project(Collection<DocEntityModel> models) {
        return project(models, EntityDocumentationExposurePolicy.allowAll());
    }

    /**
     * 使用显式暴露策略投影 DOC 模型。
     *
     * @param models DOC Runtime Model 集合
     * @param exposurePolicy 实体和字段暴露策略，不能为空
     * @return 符合 Entity Documentation Contract v1 结构的 Map
     */
    public Map<String, Object> project(
        Collection<DocEntityModel> models,
        EntityDocumentationExposurePolicy exposurePolicy
    ) {
        if (exposurePolicy == null) {
            throw new IllegalArgumentException("实体文档暴露策略不能为空");
        }
        List<DocEntityModel> visibleModels = visibleModels(models, exposurePolicy);
        Map<String, DocEntityModel> modelByResource = indexModels(visibleModels);
        Map<String, Set<String>> visibleFields = indexVisibleFields(visibleModels, exposurePolicy);

        List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
        for (DocEntityModel model : visibleModels) {
            String resourceCode = trimToNull(model.resourceCode().value());
            if (resourceCode == null) {
                continue;
            }
            entities.add(projectEntity(model, exposurePolicy, resourceCode, modelByResource, visibleFields));
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("contractVersion", CONTRACT_VERSION);
        result.put("entities", entities);
        return result;
    }

    private List<DocEntityModel> visibleModels(
        Collection<DocEntityModel> models,
        EntityDocumentationExposurePolicy exposurePolicy
    ) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocEntityModel> visible = new ArrayList<DocEntityModel>();
        for (DocEntityModel model : models) {
            if (model == null || model.entityClass() == null || Boolean.TRUE.equals(model.hidden().value())
                || !exposurePolicy.isEntityExposed(model)) {
                continue;
            }
            if (trimToNull(model.resourceCode().value()) != null) {
                visible.add(model);
            }
        }
        Collections.sort(visible, new Comparator<DocEntityModel>() {
            @Override
            public int compare(DocEntityModel left, DocEntityModel right) {
                return trimToEmpty(left.resourceCode().value()).compareTo(trimToEmpty(right.resourceCode().value()));
            }
        });
        return visible;
    }

    private Map<String, DocEntityModel> indexModels(List<DocEntityModel> models) {
        LinkedHashMap<String, DocEntityModel> result = new LinkedHashMap<String, DocEntityModel>();
        for (DocEntityModel model : models) {
            String resourceCode = trimToNull(model.resourceCode().value());
            String lookupKey = resourceCode.toLowerCase(Locale.ROOT);
            if (result.containsKey(lookupKey)) {
                throw new IllegalArgumentException("公共实体文档 resourceCode 重复: " + resourceCode);
            }
            result.put(lookupKey, model);
        }
        return result;
    }

    private Map<String, Set<String>> indexVisibleFields(
        List<DocEntityModel> models,
        EntityDocumentationExposurePolicy exposurePolicy
    ) {
        Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        for (DocEntityModel model : models) {
            Set<String> fields = new HashSet<String>();
            for (DocFieldModel field : model.fields()) {
                if (field == null || Boolean.TRUE.equals(field.hidden().value())
                    || !exposurePolicy.isFieldExposed(model, field)) {
                    continue;
                }
                String property = trimToNull(field.property());
                if (property != null) {
                    fields.add(property);
                }
            }
            result.put(trimToNull(model.resourceCode().value()).toLowerCase(Locale.ROOT), fields);
        }
        return result;
    }

    private Map<String, Object> projectEntity(
        DocEntityModel model,
        EntityDocumentationExposurePolicy exposurePolicy,
        String resourceCode,
        Map<String, DocEntityModel> modelByResource,
        Map<String, Set<String>> visibleFields
    ) {
        LinkedHashMap<String, Object> entity = new LinkedHashMap<String, Object>();
        entity.put("resourceCode", resourceCode);
        putText(entity, "name", model.entityName().value());
        putText(entity, "description", model.description().value());
        putText(entity, "group", model.group().value());
        putText(entity, "remark", model.remark().value());
        entity.put("fields", projectFields(model, exposurePolicy));
        entity.put("relations", projectRelations(model, modelByResource, visibleFields));
        entity.put("indexes", projectIndexes(model, visibleFields));
        return entity;
    }

    private List<Map<String, Object>> projectFields(
        DocEntityModel model,
        EntityDocumentationExposurePolicy exposurePolicy
    ) {
        List<DocFieldModel> source = new ArrayList<DocFieldModel>();
        for (DocFieldModel field : model.fields()) {
            if (field != null && !Boolean.TRUE.equals(field.hidden().value())
                && exposurePolicy.isFieldExposed(model, field) && trimToNull(field.property()) != null) {
                source.add(field);
            }
        }
        Collections.sort(source, new Comparator<DocFieldModel>() {
            @Override
            public int compare(DocFieldModel left, DocFieldModel right) {
                return trimToEmpty(left.property()).compareTo(trimToEmpty(right.property()));
            }
        });

        Set<String> relationFields = new HashSet<String>();
        for (DocRelationModel relation : model.relations()) {
            if (relation != null && trimToNull(relation.relationField()) != null) {
                relationFields.add(relation.relationField());
            }
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (DocFieldModel field : source) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            String property = trimToNull(field.property());
            item.put("property", property);
            item.put("type", logicalType(field.javaType(), field.fieldKind().value()));
            putText(item, "kind", field.fieldKind().value());
            putText(item, "label", field.name().value());
            putText(item, "description", field.description().value());
            putText(item, "role", field.role().value());
            putExamples(item, field);
            putBoolean(item, "required", field.required().value());
            putBoolean(item, "readOnly", field.readOnly().value());
            putInteger(item, "minLength", field.minLength().value());
            putInteger(item, "maxLength", field.maxLength().value());
            item.put("relation", Boolean.valueOf(relationFields.contains(property)));
            putConstraints(item, field.constraints());
            putEnumValues(item, field.javaType());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> projectRelations(
        DocEntityModel model,
        Map<String, DocEntityModel> modelByResource,
        Map<String, Set<String>> visibleFields
    ) {
        if (model.relations() == null || model.relations().isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        String sourceCode = trimToNull(model.resourceCode().value()).toLowerCase(Locale.ROOT);
        Set<String> sourceFields = visibleFields.get(sourceCode);
        for (DocRelationModel relation : model.relations()) {
            if (relation == null) {
                continue;
            }
            String relationField = trimToNull(relation.relationField());
            String sourceField = trimToNull(relation.sourceField().value());
            String targetField = trimToNull(relation.targetField().value());
            String targetCode = resolveTargetResourceCode(relation.targetEntity().value(), modelByResource);
            if (relationField == null || sourceField == null || targetField == null || targetCode == null
                || sourceFields == null || !sourceFields.contains(relationField) || !sourceFields.contains(sourceField)) {
                continue;
            }
            Set<String> targetFields = visibleFields.get(targetCode.toLowerCase(Locale.ROOT));
            if (targetFields == null || !targetFields.contains(targetField)) {
                continue;
            }
            String cardinality = relation.cardinality().value() == null ? null : relation.cardinality().value().name();
            String ownerSide = relation.ownerSide().value() == null ? null : relation.ownerSide().value().name();
            String resolutionStatus = relation.resolutionStatus().value() == null
                ? null : relation.resolutionStatus().value().name();
            if (cardinality == null || ownerSide == null || resolutionStatus == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("relationField", relationField);
            item.put("targetResourceCode", targetCode);
            putText(item, "targetService", relation.targetService().value());
            item.put("sourceField", sourceField);
            item.put("targetField", targetField);
            item.put("cardinality", cardinality);
            item.put("ownerSide", ownerSide);
            item.put("resolutionStatus", resolutionStatus);
            item.put("sourceFieldInferred", Boolean.valueOf(relation.sourceFieldInferred()));
            putText(item, "remark", relation.relationRemark().value());
            result.add(item);
        }
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                String leftKey = String.valueOf(left.get("relationField")) + "\u0000"
                    + String.valueOf(left.get("targetResourceCode")) + "\u0000"
                    + String.valueOf(left.get("sourceField"));
                String rightKey = String.valueOf(right.get("relationField")) + "\u0000"
                    + String.valueOf(right.get("targetResourceCode")) + "\u0000"
                    + String.valueOf(right.get("sourceField"));
                return leftKey.compareTo(rightKey);
            }
        });
        return result;
    }

    private String resolveTargetResourceCode(String targetEntity, Map<String, DocEntityModel> modelByResource) {
        String target = trimToNull(targetEntity);
        if (target == null) {
            return null;
        }
        DocEntityModel direct = modelByResource.get(target.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return trimToNull(direct.resourceCode().value());
        }
        for (DocEntityModel model : modelByResource.values()) {
            if (target.equalsIgnoreCase(trimToEmpty(model.entityName().value()))
                || (model.entityClass() != null && target.equalsIgnoreCase(model.entityClass().getSimpleName()))) {
                return trimToNull(model.resourceCode().value());
            }
        }
        return null;
    }

    private List<Map<String, Object>> projectIndexes(DocEntityModel model, Map<String, Set<String>> visibleFields) {
        if (model.indexes() == null || model.indexes().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> fields = visibleFields.get(trimToNull(model.resourceCode().value()).toLowerCase(Locale.ROOT));
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (DocIndexModel index : model.indexes()) {
            if (index == null || index.fields() == null || index.fields().isEmpty() || fields == null) {
                continue;
            }
            List<String> indexFields = new ArrayList<String>();
            boolean safe = true;
            for (String field : index.fields()) {
                String property = trimToNull(field);
                if (property == null || !fields.contains(property)) {
                    safe = false;
                    break;
                }
                indexFields.add(property);
            }
            if (!safe || index.unique().value() == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("fields", indexFields);
            item.put("unique", index.unique().value());
            result.add(item);
        }
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                String leftKey = String.valueOf(left.get("fields")) + "\u0000" + String.valueOf(left.get("unique"));
                String rightKey = String.valueOf(right.get("fields")) + "\u0000" + String.valueOf(right.get("unique"));
                return leftKey.compareTo(rightKey);
            }
        });
        return result;
    }

    private void putExamples(Map<String, Object> target, DocFieldModel field) {
        LinkedHashSet<String> examples = new LinkedHashSet<String>();
        addText(examples, field.example().value());
        if (field.examples() != null) {
            for (String example : field.examples()) {
                addText(examples, example);
            }
        }
        if (!examples.isEmpty()) {
            target.put("examples", new ArrayList<String>(examples));
        }
    }

    private void putConstraints(Map<String, Object> target, List<DocFieldConstraintModel> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return;
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (DocFieldConstraintModel constraint : constraints) {
            if (constraint == null || trimToNull(constraint.name()) == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", trimToNull(constraint.name()));
            item.put("value", constraint.value() == null ? "" : constraint.value());
            result.add(item);
        }
        if (!result.isEmpty()) {
            Collections.sort(result, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name")));
                }
            });
            target.put("constraints", result);
        }
    }

    private void putEnumValues(Map<String, Object> target, Class<?> javaType) {
        if (javaType == null || !javaType.isEnum() || javaType.getEnumConstants() == null) {
            return;
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object constant : javaType.getEnumConstants()) {
            if (!(constant instanceof Enum)) {
                continue;
            }
            Enum<?> enumConstant = (Enum<?>) constant;
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", enumConstant.name());
            String label = resolveEnumLabel(javaType, constant);
            if (label != null) {
                item.put("label", label);
            }
            result.add(item);
        }
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                return String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name")));
            }
        });
        if (!result.isEmpty()) {
            target.put("enumValues", result);
        }
    }

    private String resolveEnumLabel(Class<?> enumType, Object enumConstant) {
        String[] names = {"getLabel", "label", "getDesc", "desc", "getDescription", "description"};
        for (String name : names) {
            String value = name.startsWith("get")
                ? readEnumGetter(enumType, enumConstant, name)
                : readEnumField(enumType, enumConstant, name);
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String readEnumGetter(Class<?> type, Object target, String name) {
        try {
            Method method = type.getMethod(name);
            if (Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            Object value = method.invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String readEnumField(Class<?> type, Object target, String name) {
        try {
            java.lang.reflect.Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String logicalType(Class<?> javaType, String fieldKind) {
        if (javaType != null && javaType.isEnum()) {
            return "enum";
        }
        if (javaType != null && javaType.isArray()) {
            return javaType.getComponentType() == Byte.TYPE || javaType.getComponentType() == Byte.class
                ? "binary" : "array";
        }
        if (javaType != null && Collection.class.isAssignableFrom(javaType)) {
            return "array";
        }
        if (javaType != null && Map.class.isAssignableFrom(javaType)) {
            return "object";
        }
        if (javaType == String.class || (javaType != null && CharSequence.class.isAssignableFrom(javaType))
            || javaType == Character.class || javaType == Character.TYPE) {
            return "string";
        }
        if (javaType == Boolean.class || javaType == Boolean.TYPE) {
            return "boolean";
        }
        if (javaType == Byte.class || javaType == Byte.TYPE || javaType == Short.class || javaType == Short.TYPE
            || javaType == Integer.class || javaType == Integer.TYPE || javaType == Long.class || javaType == Long.TYPE
            || javaType == BigInteger.class) {
            return "integer";
        }
        if (javaType == Float.class || javaType == Float.TYPE || javaType == Double.class || javaType == Double.TYPE
            || javaType == BigDecimal.class || (javaType != null && Number.class.isAssignableFrom(javaType))) {
            return "number";
        }
        if (javaType == LocalDate.class) {
            return "date";
        }
        if (javaType == LocalDateTime.class || javaType == OffsetDateTime.class || javaType == ZonedDateTime.class
            || javaType == Instant.class || javaType == java.util.Date.class) {
            return "date-time";
        }
        String kind = trimToNull(fieldKind);
        if (kind != null) {
            if ("FLAG".equals(kind)) {
                return "boolean";
            }
            if ("DATETIME".equals(kind)) {
                return "date-time";
            }
            if ("ENUM".equals(kind)) {
                return "enum";
            }
            if ("JSON_DOC".equals(kind)) {
                return "object";
            }
            if ("MEDIA".equals(kind)) {
                return "binary";
            }
            if ("NUMBER".equals(kind)) {
                return "number";
            }
            if ("TEXT".equals(kind) || "RICH_CONTENT".equals(kind)) {
                return "string";
            }
        }
        return "unknown";
    }

    private void putText(Map<String, Object> target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private void putBoolean(Map<String, Object> target, String key, Boolean value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putInteger(Map<String, Object> target, String key, Integer value) {
        if (value != null && value.intValue() >= 0) {
            target.put(key, value);
        }
    }

    private void addText(Set<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToEmpty(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "" : normalized;
    }
}
