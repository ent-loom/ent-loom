package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.base.util.value.TypedValueCodec;
import com.entloom.base.util.value.TypedValueType;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaFlag;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaJson;
import com.entloom.meta.annotations.meta.EntMetaMedia;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaRichContent;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntIndexDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCollector;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.core.descriptor.DefaultEntEntityDescriptor;
import com.entloom.meta.core.descriptor.DefaultEntFieldConstraintDescriptor;
import com.entloom.meta.core.descriptor.DefaultEntFieldDescriptor;
import com.entloom.meta.core.descriptor.DefaultEntIndexDescriptor;
import com.entloom.meta.core.descriptor.DefaultEntRelationDescriptor;
import com.entloom.meta.contract.value.SourcedValue;
import com.entloom.meta.enums.EntFieldKind;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflection-based parser for Ent meta annotations.
 */
public class ReflectiveEntMetaParser implements EntMetaParser {

    @Override
    public EntEntityDescriptor parse(Class<?> entityClass) {
        MetaDiagnosticResult<EntEntityDescriptor> result = parseWithDiagnostics(entityClass);
        DefaultMetaDiagnosticPolicy.failFast().evaluate(result.diagnostics());
        return result.value();
    }

    @Override
    public MetaDiagnosticResult<EntEntityDescriptor> parseWithDiagnostics(Class<?> entityClass) {
        MetaDiagnosticCollector diagnostics = new MetaDiagnosticCollector();
        if (entityClass == null) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.MISSING_ENTITY_CLASS)
                .property(MetaDescriptorProperties.ENTITY_CLASS)
                .message("entityClass 不能为空")
                .build());
            return MetaDiagnosticResult.of(null, diagnostics.diagnostics());
        }
        EntEntity entity = entityClass.getAnnotation(EntEntity.class);
        if (entity == null) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.MISSING_ENTITY_ANNOTATION)
                .entityClass(entityClass)
                .location(entityClass.getName())
                .message("缺少 @EntEntity 注解: " + entityClass.getName())
                .build());
            return MetaDiagnosticResult.of(null, diagnostics.diagnostics());
        }

        List<EntFieldDescriptor> fields = new ArrayList<EntFieldDescriptor>();
        List<EntRelationDescriptor> relations = new ArrayList<EntRelationDescriptor>();
        Set<String> javaFieldNames = new LinkedHashSet<String>();
        for (Field field : getAllFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            javaFieldNames.add(field.getName());
            EntField entField = findAnnotation(field, EntField.class);
            EntRelation relation = findAnnotation(field, EntRelation.class);
            if (shouldDescribeAsField(field, entField, relation)) {
                fields.add(toFieldDescriptor(field, entField, diagnostics));
            }
            if (relation != null) {
                relations.add(toRelationDescriptor(field, relation));
            }
        }

        List<EntIndexDescriptor> indexes = indexes(entityClass, entity);
        collectFieldDiagnostics(entityClass, entity, fields, diagnostics);
        collectRelationDiagnostics(entityClass, entity, relations, javaFieldNames, diagnostics);
        collectIndexDiagnostics(entityClass, entity, indexes, javaFieldNames, diagnostics);

        EntEntityDescriptor descriptor = new DefaultEntEntityDescriptor(
            entityClass,
            entity.entity(),
            emptyToNull(entity.service()),
            emptyToNull(entity.label()),
            emptyToNull(entity.description()),
            Arrays.asList(entity.defaultLabelFields()),
            entity.plannedVolume() < 0L ? null : Long.valueOf(entity.plannedVolume()),
            fields,
            relations,
            indexes,
            entitySources(entityClass, entity)
        );
        return MetaDiagnosticResult.of(descriptor, diagnostics.diagnostics());
    }

    private EntFieldDescriptor toFieldDescriptor(Field field, EntField entField, MetaDiagnosticCollector diagnostics) {
        EntFieldKind kind = fieldKind(field, entField);
        boolean explicitField = entField != null;
        TypedDefaultValue defaultValue = typedDefaultValue(field, entField, diagnostics);
        SourcedValue<String> role = fieldRole(field, kind);
        List<EntFieldConstraintDescriptor> constraints = constraints(field);
        Map<String, SourcedValue<?>> sources = fieldSources(field, entField, kind, explicitField, defaultValue, role, constraints);
        return new DefaultEntFieldDescriptor(
            field.getName(),
            field.getType(),
            kind.name(),
            role.value(),
            entField == null ? null : emptyToNull(entField.label()),
            entField == null ? null : emptyToNull(entField.description()),
            entField == null ? java.util.Collections.<String>emptyList() : Arrays.asList(entField.examples()),
            defaultValue.rawValue,
            defaultValue.valueType,
            defaultValue.typedValue,
            constraints,
            entField == null ? null : toBoolean(entField.required()),
            entField == null ? null : toBoolean(entField.readOnly()),
            sources
        );
    }

    private EntRelationDescriptor toRelationDescriptor(Field field, EntRelation relation) {
        String sourceField = relation.sourceField();
        boolean sourceFieldInferred = isBlank(sourceField);
        if (sourceFieldInferred) {
            sourceField = field.getName();
        }
        String targetEntity = emptyToNull(relation.targetEntity());
        String targetField = isBlank(relation.targetField()) ? "id" : relation.targetField();
        RelationCardinality cardinality = relation.cardinality();
        RelationOwnerSide ownerSide = ownerSide(cardinality);
        RelationResolutionStatus resolutionStatus = isBlank(targetEntity)
            ? RelationResolutionStatus.INVALID
            : RelationResolutionStatus.PARTIALLY_RESOLVED;
        return new DefaultEntRelationDescriptor(
            sourceField,
            emptyToNull(relation.targetService()),
            targetEntity,
            targetField,
            cardinality,
            ownerSide,
            resolutionStatus,
            sourceFieldInferred,
            relationSources(field, relation, sourceField, targetField, cardinality, ownerSide, resolutionStatus, sourceFieldInferred)
        );
    }

    private List<EntIndexDescriptor> indexes(Class<?> entityClass, EntEntity entity) {
        List<EntIndexDescriptor> indexes = new ArrayList<EntIndexDescriptor>();
        for (EntIndex index : entityClass.getAnnotationsByType(EntIndex.class)) {
            indexes.add(toIndexDescriptor(index));
        }
        return indexes;
    }

    private EntIndexDescriptor toIndexDescriptor(EntIndex index) {
        return new DefaultEntIndexDescriptor(
            emptyToNull(index.name()),
            Arrays.asList(index.fields()),
            index.unique(),
            indexSources(index)
        );
    }

    private void collectFieldDiagnostics(
        Class<?> entityClass,
        EntEntity entity,
        List<EntFieldDescriptor> fields,
        MetaDiagnosticCollector diagnostics
    ) {
        for (EntFieldDescriptor field : fields) {
            Field javaField = findDeclaredField(entityClass, field.fieldName());
            if (javaField == null) {
                continue;
            }
            EntFieldKind kind = EntFieldKind.valueOf(field.fieldKind());
            collectFieldKindMetaDiagnostics(entityClass, entity, javaField, kind, diagnostics);
            collectConstraintDiagnostics(entityClass, entity, javaField, diagnostics);
        }
    }

    private void collectFieldKindMetaDiagnostics(
        Class<?> entityClass,
        EntEntity entity,
        Field field,
        EntFieldKind kind,
        MetaDiagnosticCollector diagnostics
    ) {
        if (findAnnotation(field, EntMetaId.class) != null && kind != EntFieldKind.ID) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaId", EntFieldKind.ID, diagnostics);
        }
        if (findAnnotation(field, EntRelation.class) != null && kind != EntFieldKind.REF_ID) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntRelation", EntFieldKind.REF_ID, diagnostics);
        }
        if (findAnnotation(field, EntMetaText.class) != null && kind != EntFieldKind.TEXT) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaText", EntFieldKind.TEXT, diagnostics);
        }
        if (findAnnotation(field, EntMetaNumber.class) != null && kind != EntFieldKind.NUMBER) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaNumber", EntFieldKind.NUMBER, diagnostics);
        }
        if (findAnnotation(field, EntMetaEnum.class) != null && kind != EntFieldKind.ENUM) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaEnum", EntFieldKind.ENUM, diagnostics);
        }
        if (findAnnotation(field, EntMetaFlag.class) != null && kind != EntFieldKind.FLAG) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaFlag", EntFieldKind.FLAG, diagnostics);
        }
        if (findAnnotation(field, EntMetaDateTime.class) != null && kind != EntFieldKind.DATETIME) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaDateTime", EntFieldKind.DATETIME, diagnostics);
        }
        if (findAnnotation(field, EntMetaMedia.class) != null && kind != EntFieldKind.MEDIA) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaMedia", EntFieldKind.MEDIA, diagnostics);
        }
        if (findAnnotation(field, EntMetaJson.class) != null && kind != EntFieldKind.JSON_DOC) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaJson", EntFieldKind.JSON_DOC, diagnostics);
        }
        if (findAnnotation(field, EntMetaRichContent.class) != null && kind != EntFieldKind.RICH_CONTENT) {
            addKindMismatchDiagnostic(entityClass, entity, field, kind, "EntMetaRichContent", EntFieldKind.RICH_CONTENT, diagnostics);
        }
    }

    private void addKindMismatchDiagnostic(
        Class<?> entityClass,
        EntEntity entity,
        Field field,
        EntFieldKind actual,
        String annotationName,
        EntFieldKind expected,
        MetaDiagnosticCollector diagnostics
    ) {
        diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.FIELD_KIND_META_MISMATCH)
            .entity(entity.entity())
            .entityClass(entityClass)
            .field(field.getName())
            .property(MetaDescriptorProperties.FIELD_KIND)
            .location(entityClass.getName() + "#" + field.getName())
            .message(annotationName + " 只能用于 " + expected.name() + " 字段，当前为 " + actual.name())
            .build());
    }

    private void collectConstraintDiagnostics(
        Class<?> entityClass,
        EntEntity entity,
        Field field,
        MetaDiagnosticCollector diagnostics
    ) {
        EntMetaNumber number = findAnnotation(field, EntMetaNumber.class);
        if (number != null) {
            if (number.precision() >= 0 && number.scale() > number.precision()) {
                addInvalidConstraintDiagnostic(entityClass, entity, field, "number.scale", "scale 不能大于 precision", diagnostics);
            }
            BigDecimal min = parseDecimalConstraint(entityClass, entity, field, "number.min", number.min(), diagnostics);
            BigDecimal max = parseDecimalConstraint(entityClass, entity, field, "number.max", number.max(), diagnostics);
            if (min != null && max != null && min.compareTo(max) > 0) {
                addInvalidConstraintDiagnostic(entityClass, entity, field, "number.min", "min 不能大于 max", diagnostics);
            }
            BigDecimal step = parseDecimalConstraint(entityClass, entity, field, "number.step", number.step(), diagnostics);
            if (step != null && step.compareTo(BigDecimal.ZERO) <= 0) {
                addInvalidConstraintDiagnostic(entityClass, entity, field, "number.step", "step 必须大于 0", diagnostics);
            }
        }
        EntMetaEnum enumMeta = findAnnotation(field, EntMetaEnum.class);
        if (enumMeta != null
            && enumMeta.maxSelections() >= 0
            && enumMeta.cardinality() != EntMetaEnum.Cardinality.MULTI) {
            addInvalidConstraintDiagnostic(entityClass, entity, field, "enum.maxSelections", "maxSelections 仅适用于 MULTI 枚举", diagnostics);
        }
        EntMetaDateTime dateTime = findAnnotation(field, EntMetaDateTime.class);
        if (dateTime != null && !isBlank(dateTime.timezone())) {
            try {
                ZoneId.of(dateTime.timezone().trim());
            } catch (DateTimeException ex) {
                addInvalidConstraintDiagnostic(entityClass, entity, field, "dateTime.timezone", "timezone 不是有效 ZoneId", diagnostics);
            }
        }
    }

    private BigDecimal parseDecimalConstraint(
        Class<?> entityClass,
        EntEntity entity,
        Field field,
        String property,
        String rawValue,
        MetaDiagnosticCollector diagnostics
    ) {
        String normalized = emptyToNull(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            addInvalidConstraintDiagnostic(entityClass, entity, field, property, "数值约束不是合法十进制数", diagnostics);
            return null;
        }
    }

    private void addInvalidConstraintDiagnostic(
        Class<?> entityClass,
        EntEntity entity,
        Field field,
        String property,
        String message,
        MetaDiagnosticCollector diagnostics
    ) {
        diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.INVALID_FIELD_CONSTRAINT)
            .entity(entity.entity())
            .entityClass(entityClass)
            .field(field.getName())
            .property(property)
            .location(entityClass.getName() + "#" + field.getName())
            .message(message + ": " + field.getName())
            .build());
    }

    private void collectRelationDiagnostics(
        Class<?> entityClass,
        EntEntity entity,
        List<EntRelationDescriptor> relations,
        Set<String> javaFieldNames,
        MetaDiagnosticCollector diagnostics
    ) {
        for (EntRelationDescriptor relation : relations) {
            if (!javaFieldNames.contains(relation.sourceField())) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND)
                    .entity(entity.entity())
                    .entityClass(entityClass)
                    .field(relation.sourceField())
                    .property(MetaDescriptorProperties.SOURCE_FIELD)
                    .location(entityClass.getName() + "#" + relation.sourceField())
                    .message("关系 sourceField 不存在: " + relation.sourceField())
                    .build());
            }
            if (isBlank(relation.targetEntity())) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND)
                    .entity(entity.entity())
                    .entityClass(entityClass)
                    .field(relation.sourceField())
                    .property(MetaDescriptorProperties.TARGET_ENTITY)
                    .location(entityClass.getName() + "#" + relation.sourceField())
                    .message("关系 targetEntity 不能为空: " + relation.sourceField())
                    .build());
            }
            SourcedValue<?> sourceFieldSource = relation.sourcedValue(MetaDescriptorProperties.SOURCE_FIELD);
            if (sourceFieldSource != null && sourceFieldSource.source() == com.entloom.meta.contract.value.MetaValueSource.INFERRED) {
                diagnostics.add(MetaDiagnostic.info(MetaDiagnosticCode.INFERRED_VALUE_USED)
                    .entity(entity.entity())
                    .entityClass(entityClass)
                    .field(relation.sourceField())
                    .source(sourceFieldSource.source())
                    .property(MetaDescriptorProperties.SOURCE_FIELD)
                    .location(entityClass.getName() + "#" + relation.sourceField())
                    .message("关系 sourceField 由字段名推断: " + relation.sourceField())
                    .build());
            }
        }
    }

    private void collectIndexDiagnostics(
        Class<?> entityClass,
        EntEntity entity,
        List<EntIndexDescriptor> indexes,
        Set<String> javaFieldNames,
        MetaDiagnosticCollector diagnostics
    ) {
        Set<String> indexNames = new LinkedHashSet<String>();
        for (EntIndexDescriptor index : indexes) {
            if (!isBlank(index.indexName()) && !indexNames.add(index.indexName())) {
                diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.DUPLICATE_INDEX)
                    .entity(entity.entity())
                    .entityClass(entityClass)
                    .property(MetaDescriptorProperties.INDEX_NAME)
                    .location(entityClass.getName() + "#" + index.indexName())
                    .message("索引名重复: " + index.indexName())
                    .build());
            }
            for (String fieldName : index.fields()) {
                if (!javaFieldNames.contains(fieldName)) {
                    diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.INDEX_FIELD_NOT_FOUND)
                        .entity(entity.entity())
                        .entityClass(entityClass)
                        .field(fieldName)
                        .property(MetaDescriptorProperties.FIELDS)
                        .location(entityClass.getName() + "#" + fieldName)
                        .message("索引字段不存在: " + fieldName)
                        .build());
                }
            }
        }
    }

    private EntFieldKind fieldKind(Field field, EntField entField) {
        if (entField != null) {
            return entField.value();
        }
        return inferFieldKind(field);
    }

    private boolean shouldDescribeAsField(Field field, EntField entField, EntRelation relation) {
        if (entField != null || relation == null) {
            return true;
        }
        return isScalarValueType(field.getType());
    }

    private EntFieldKind inferFieldKind(Field field) {
        String fieldName = field.getName();
        Class<?> javaType = field.getType();
        if ("id".equals(fieldName)) {
            return EntFieldKind.ID;
        }
        if (findAnnotation(field, EntRelation.class) != null || fieldName.endsWith("Id")) {
            return EntFieldKind.REF_ID;
        }
        if (javaType != null && javaType.isEnum()) {
            return EntFieldKind.ENUM;
        }
        if (Boolean.class.equals(javaType) || boolean.class.equals(javaType)) {
            return EntFieldKind.FLAG;
        }
        if (LocalDate.class.equals(javaType) || LocalDateTime.class.equals(javaType)
            || Instant.class.equals(javaType) || Date.class.equals(javaType)) {
            return EntFieldKind.DATETIME;
        }
        if (Number.class.isAssignableFrom(wrapPrimitive(javaType))) {
            return EntFieldKind.NUMBER;
        }
        String lowerName = fieldName.toLowerCase();
        if (String.class.equals(javaType) && (lowerName.endsWith("json") || lowerName.endsWith("jsontext"))) {
            return EntFieldKind.JSON_DOC;
        }
        if (String.class.equals(javaType)
            && (lowerName.endsWith("url") || lowerName.endsWith("uri") || lowerName.endsWith("path"))
            && (lowerName.contains("image") || lowerName.contains("avatar") || lowerName.contains("cover")
                || lowerName.contains("media") || lowerName.contains("file"))) {
            return EntFieldKind.MEDIA;
        }
        return EntFieldKind.TEXT;
    }

    private boolean isScalarValueType(Class<?> javaType) {
        Class<?> type = wrapPrimitive(javaType);
        return type != null
            && (CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || type.isEnum()
                || LocalDate.class.equals(type)
                || LocalDateTime.class.equals(type)
                || Instant.class.equals(type)
                || Date.class.equals(type));
    }

    private Class<?> wrapPrimitive(Class<?> javaType) {
        if (javaType == null || !javaType.isPrimitive()) {
            return javaType;
        }
        if (int.class.equals(javaType)) {
            return Integer.class;
        }
        if (long.class.equals(javaType)) {
            return Long.class;
        }
        if (short.class.equals(javaType)) {
            return Short.class;
        }
        if (byte.class.equals(javaType)) {
            return Byte.class;
        }
        if (double.class.equals(javaType)) {
            return Double.class;
        }
        if (float.class.equals(javaType)) {
            return Float.class;
        }
        if (boolean.class.equals(javaType)) {
            return Boolean.class;
        }
        return javaType;
    }

    private TypedDefaultValue typedDefaultValue(Field field, EntField entField, MetaDiagnosticCollector diagnostics) {
        if (entField == null) {
            return new TypedDefaultValue(null, TypedValueType.UNSET, null);
        }
        String rawValue = emptyToNull(entField.createDefaultValue());
        if (rawValue == null) {
            return new TypedDefaultValue(null, TypedValueType.UNSET, null);
        }
        TypedValueType valueType = entField.createDefaultValueType();
        SourcedValue<TypedValueType> valueTypeSource;
        if (valueType == null || valueType == TypedValueType.UNSET) {
            valueType = TypedValueCodec.inferType(field.getType());
            valueTypeSource = SourcedValue.inferred(valueType);
        } else {
            valueTypeSource = SourcedValue.metaExplicit(valueType);
        }
        Object typedValue = null;
        try {
            typedValue = TypedValueCodec.deserialize(rawValue, valueType);
        } catch (RuntimeException ex) {
            diagnostics.add(MetaDiagnostic.error(MetaDiagnosticCode.INVALID_DEFAULT_VALUE)
                .field(field.getName())
                .property(MetaDescriptorProperties.CREATE_DEFAULT_VALUE)
                .location(field.getDeclaringClass().getName() + "#" + field.getName())
                .message("创建默认值无法按指定类型解析: " + field.getName())
                .build());
        }
        return new TypedDefaultValue(rawValue, valueType, typedValue, valueTypeSource, SourcedValue.inferred(typedValue));
    }

    private SourcedValue<String> fieldRole(Field field, EntFieldKind kind) {
        if (kind == EntFieldKind.ID) {
            return findAnnotation(field, EntMetaId.class) != null ? SourcedValue.metaExplicit("ID") : SourcedValue.inferred("ID");
        }
        if (kind == EntFieldKind.REF_ID) {
            EntRelation relation = findAnnotation(field, EntRelation.class);
            if (relation != null && relation.role() != null && !"UNSET".equals(relation.role().name())) {
                return SourcedValue.metaExplicit("REF_ID." + relation.role().name());
            }
        }
        EntMetaText text = findAnnotation(field, EntMetaText.class);
        if (text != null && text.value() != null && !"UNSET".equals(text.value().name())) {
            return SourcedValue.metaExplicit("TEXT." + text.value().name());
        }
        EntMetaNumber number = findAnnotation(field, EntMetaNumber.class);
        if (number != null && number.value() != null && !"UNSET".equals(number.value().name())) {
            return SourcedValue.metaExplicit("NUMBER." + number.value().name());
        }
        EntMetaEnum enumMeta = findAnnotation(field, EntMetaEnum.class);
        if (enumMeta != null && enumMeta.value() != null && !"UNSET".equals(enumMeta.value().name())) {
            return SourcedValue.metaExplicit("ENUM." + enumMeta.value().name());
        }
        EntMetaFlag flag = findAnnotation(field, EntMetaFlag.class);
        if (flag != null && flag.value() != null && !"UNSET".equals(flag.value().name())) {
            return SourcedValue.metaExplicit("FLAG." + flag.value().name());
        }
        EntMetaDateTime dateTime = findAnnotation(field, EntMetaDateTime.class);
        if (dateTime != null && dateTime.value() != null && !"UNSET".equals(dateTime.value().name())) {
            return SourcedValue.metaExplicit("DATETIME." + dateTime.value().name());
        }
        EntMetaMedia media = findAnnotation(field, EntMetaMedia.class);
        if (media != null && media.value() != null && !"UNSET".equals(media.value().name())) {
            return SourcedValue.metaExplicit("MEDIA." + media.value().name());
        }
        EntMetaJson json = findAnnotation(field, EntMetaJson.class);
        if (json != null && json.value() != null && !"UNSET".equals(json.value().name())) {
            return SourcedValue.metaExplicit("JSON_DOC." + json.value().name());
        }
        return SourcedValue.unknown(null);
    }

    private List<EntFieldConstraintDescriptor> constraints(Field field) {
        List<EntFieldConstraintDescriptor> constraints = new ArrayList<EntFieldConstraintDescriptor>();
        EntMetaText text = findAnnotation(field, EntMetaText.class);
        if (text != null) {
            addBooleanConstraint(constraints, "text.multiline", toBoolean(text.multiline()));
            addIntConstraint(constraints, "text.maxLength", text.maxLength());
            addStringConstraint(constraints, "text.pattern", text.pattern());
            addEnumConstraint(constraints, "text.masking", text.masking());
        }
        EntMetaNumber number = findAnnotation(field, EntMetaNumber.class);
        if (number != null) {
            addIntConstraint(constraints, "number.precision", number.precision());
            addIntConstraint(constraints, "number.scale", number.scale());
            addStringConstraint(constraints, "number.min", number.min());
            addStringConstraint(constraints, "number.max", number.max());
            addStringConstraint(constraints, "number.step", number.step());
            addStringConstraint(constraints, "number.unit", number.unit());
        }
        EntMetaEnum enumMeta = findAnnotation(field, EntMetaEnum.class);
        if (enumMeta != null) {
            if (enumMeta.enumClass() != EntMetaEnum.UnspecifiedEnum.class) {
                addStringConstraint(constraints, "enum.class", enumMeta.enumClass().getName());
            }
            addEnumConstraint(constraints, "enum.valueType", enumMeta.valueType());
            addEnumConstraint(constraints, "enum.cardinality", enumMeta.cardinality());
            addIntConstraint(constraints, "enum.maxSelections", enumMeta.maxSelections());
        }
        EntMetaJson json = findAnnotation(field, EntMetaJson.class);
        if (json != null) {
            addEnumConstraint(constraints, "json.validateMode", json.validateMode());
            addStringConstraint(constraints, "json.schemaRef", json.schemaRef());
            addLongConstraint(constraints, "json.maxBytes", json.maxBytes());
        }
        EntMetaMedia media = findAnnotation(field, EntMetaMedia.class);
        if (media != null) {
            addEnumConstraint(constraints, "media.pathMode", media.pathMode());
            addStringArrayConstraint(constraints, "media.accept", media.accept());
            addIntConstraint(constraints, "media.maxCount", media.maxCount());
            addLongConstraint(constraints, "media.maxSize", media.maxSize());
        }
        EntMetaRichContent richContent = findAnnotation(field, EntMetaRichContent.class);
        if (richContent != null) {
            addEnumConstraint(constraints, "richContent.format", richContent.format());
            addEnumConstraint(constraints, "richContent.sanitizePolicy", richContent.sanitizePolicy());
            addIntConstraint(constraints, "richContent.maxLength", richContent.maxLength());
        }
        EntMetaDateTime dateTime = findAnnotation(field, EntMetaDateTime.class);
        if (dateTime != null) {
            addEnumConstraint(constraints, "dateTime.autoFill", dateTime.autoFill());
            addEnumConstraint(constraints, "dateTime.encoding", dateTime.encoding());
            addStringConstraint(constraints, "dateTime.timezone", dateTime.timezone());
        }
        EntMetaId id = findAnnotation(field, EntMetaId.class);
        if (id != null) {
            addEnumConstraint(constraints, "id.generator", id.generator());
        }
        return constraints;
    }

    private void addBooleanConstraint(List<EntFieldConstraintDescriptor> constraints, String name, Boolean value) {
        if (value != null) {
            constraints.add(newConstraint(name, String.valueOf(value)));
        }
    }

    private void addIntConstraint(List<EntFieldConstraintDescriptor> constraints, String name, int value) {
        if (value >= 0) {
            constraints.add(newConstraint(name, String.valueOf(value)));
        }
    }

    private void addLongConstraint(List<EntFieldConstraintDescriptor> constraints, String name, long value) {
        if (value >= 0L) {
            constraints.add(newConstraint(name, String.valueOf(value)));
        }
    }

    private void addStringConstraint(List<EntFieldConstraintDescriptor> constraints, String name, String value) {
        String normalized = emptyToNull(value);
        if (normalized != null) {
            constraints.add(newConstraint(name, normalized));
        }
    }

    private void addStringArrayConstraint(List<EntFieldConstraintDescriptor> constraints, String name, String[] values) {
        if (values != null && values.length > 0) {
            constraints.add(newConstraint(name, String.join(",", values)));
        }
    }

    private void addEnumConstraint(List<EntFieldConstraintDescriptor> constraints, String name, Enum<?> value) {
        if (value != null && !"UNSET".equals(value.name())) {
            constraints.add(newConstraint(name, value.name()));
        }
    }

    private EntFieldConstraintDescriptor newConstraint(String name, String value) {
        Map<String, SourcedValue<?>> sources = new LinkedHashMap<String, SourcedValue<?>>();
        sources.put(MetaDescriptorProperties.NAME, SourcedValue.metaExplicit(name));
        sources.put(MetaDescriptorProperties.VALUE, SourcedValue.metaExplicit(value));
        return new DefaultEntFieldConstraintDescriptor(name, value, sources);
    }

    private Map<String, SourcedValue<?>> entitySources(Class<?> entityClass, EntEntity entity) {
        Map<String, SourcedValue<?>> sources = new LinkedHashMap<String, SourcedValue<?>>();
        sources.put(MetaDescriptorProperties.ENTITY_CLASS, SourcedValue.inferred(entityClass));
        sources.put(MetaDescriptorProperties.ENTITY_NAME, SourcedValue.metaExplicit(entity.entity()));
        sources.put(MetaDescriptorProperties.SERVICE_NAME, stringSource(emptyToNull(entity.service())));
        sources.put(MetaDescriptorProperties.LABEL, stringSource(emptyToNull(entity.label())));
        sources.put(MetaDescriptorProperties.DESCRIPTION, stringSource(emptyToNull(entity.description())));
        List<String> defaultLabelFields = Arrays.asList(entity.defaultLabelFields());
        sources.put(
            MetaDescriptorProperties.DEFAULT_LABEL_FIELDS,
            defaultLabelFields.isEmpty() ? SourcedValue.unknown(defaultLabelFields) : SourcedValue.metaExplicit(defaultLabelFields)
        );
        Long plannedVolume = entity.plannedVolume() < 0L ? null : Long.valueOf(entity.plannedVolume());
        sources.put(
            MetaDescriptorProperties.PLANNED_VOLUME,
            plannedVolume == null ? SourcedValue.unknown(null) : SourcedValue.metaExplicit(plannedVolume)
        );
        return sources;
    }

    private Map<String, SourcedValue<?>> fieldSources(
        Field field,
        EntField entField,
        EntFieldKind kind,
        boolean explicitField,
        TypedDefaultValue defaultValue,
        SourcedValue<String> role,
        List<EntFieldConstraintDescriptor> constraintValues
    ) {
        Map<String, SourcedValue<?>> sources = new LinkedHashMap<String, SourcedValue<?>>();
        sources.put(MetaDescriptorProperties.FIELD_NAME, SourcedValue.inferred(field.getName()));
        sources.put(MetaDescriptorProperties.JAVA_TYPE, SourcedValue.inferred(field.getType()));
        sources.put(
            MetaDescriptorProperties.FIELD_KIND,
            explicitField ? SourcedValue.metaExplicit(kind.name()) : SourcedValue.inferred(kind.name())
        );
        sources.put(MetaDescriptorProperties.ROLE, role);
        sources.put(MetaDescriptorProperties.LABEL, stringSource(entField == null ? null : emptyToNull(entField.label())));
        sources.put(MetaDescriptorProperties.DESCRIPTION, stringSource(entField == null ? null : emptyToNull(entField.description())));
        List<String> examples = entField == null ? java.util.Collections.<String>emptyList() : Arrays.asList(entField.examples());
        sources.put(MetaDescriptorProperties.EXAMPLES, examples.isEmpty() ? SourcedValue.unknown(examples) : SourcedValue.metaExplicit(examples));
        sources.put(
            MetaDescriptorProperties.CREATE_DEFAULT_VALUE,
            defaultValue.rawValue == null ? SourcedValue.unknown(null) : SourcedValue.metaExplicit(defaultValue.rawValue)
        );
        sources.put(MetaDescriptorProperties.CREATE_DEFAULT_VALUE_TYPE, defaultValue.valueTypeSource);
        sources.put(MetaDescriptorProperties.TYPED_CREATE_DEFAULT_VALUE, defaultValue.typedValueSource);
        sources.put(
            MetaDescriptorProperties.CONSTRAINTS,
            constraintValues.isEmpty() ? SourcedValue.unknown(constraintValues) : SourcedValue.metaExplicit(constraintValues)
        );
        sources.put(
            MetaDescriptorProperties.REQUIRED,
            entField == null ? SourcedValue.unknown(null) : optionalBooleanSource(entField.required(), toBoolean(entField.required()))
        );
        sources.put(
            MetaDescriptorProperties.READ_ONLY,
            entField == null ? SourcedValue.unknown(null) : optionalBooleanSource(entField.readOnly(), toBoolean(entField.readOnly()))
        );
        return sources;
    }

    private Map<String, SourcedValue<?>> relationSources(
        Field field,
        EntRelation relation,
        String resolvedSourceField,
        String targetField,
        RelationCardinality cardinality,
        RelationOwnerSide ownerSide,
        RelationResolutionStatus resolutionStatus,
        boolean sourceFieldInferred
    ) {
        Map<String, SourcedValue<?>> sources = new LinkedHashMap<String, SourcedValue<?>>();
        sources.put(
            MetaDescriptorProperties.SOURCE_FIELD,
            isBlank(relation.sourceField()) ? SourcedValue.inferred(field.getName()) : SourcedValue.metaExplicit(resolvedSourceField)
        );
        sources.put(MetaDescriptorProperties.TARGET_SERVICE, stringSource(emptyToNull(relation.targetService())));
        sources.put(MetaDescriptorProperties.TARGET_ENTITY, stringSource(emptyToNull(relation.targetEntity())));
        sources.put(
            MetaDescriptorProperties.TARGET_FIELD,
            "id".equals(targetField) ? SourcedValue.unknown(targetField) : SourcedValue.metaExplicit(targetField)
        );
        sources.put(
            MetaDescriptorProperties.CARDINALITY,
            cardinality == RelationCardinality.MANY_TO_ONE
                ? SourcedValue.unknown(cardinality)
                : SourcedValue.metaExplicit(cardinality)
        );
        sources.put(MetaDescriptorProperties.OWNER_SIDE, SourcedValue.inferred(ownerSide));
        sources.put(MetaDescriptorProperties.RESOLUTION_STATUS, SourcedValue.inferred(resolutionStatus));
        sources.put(MetaDescriptorProperties.SOURCE_FIELD_INFERRED, SourcedValue.inferred(Boolean.valueOf(sourceFieldInferred)));
        return sources;
    }

    private RelationOwnerSide ownerSide(RelationCardinality cardinality) {
        if (cardinality == RelationCardinality.ONE_TO_MANY) {
            return RelationOwnerSide.TARGET_ENTITY;
        }
        if (cardinality == RelationCardinality.MANY_TO_MANY) {
            return RelationOwnerSide.UNKNOWN;
        }
        return RelationOwnerSide.DECLARING_ENTITY;
    }

    private Map<String, SourcedValue<?>> indexSources(EntIndex index) {
        Map<String, SourcedValue<?>> sources = new LinkedHashMap<String, SourcedValue<?>>();
        String indexName = emptyToNull(index.name());
        sources.put(MetaDescriptorProperties.INDEX_NAME, indexName == null ? SourcedValue.unknown(null) : SourcedValue.metaExplicit(indexName));
        sources.put(MetaDescriptorProperties.FIELDS, SourcedValue.metaExplicit(Arrays.asList(index.fields())));
        sources.put(MetaDescriptorProperties.UNIQUE, index.unique() ? SourcedValue.metaExplicit(Boolean.TRUE) : SourcedValue.unknown(Boolean.FALSE));
        return sources;
    }

    private SourcedValue<String> stringSource(String value) {
        return value == null ? SourcedValue.unknown(null) : SourcedValue.metaExplicit(value);
    }

    private SourcedValue<Boolean> optionalBooleanSource(OptionalBoolean raw, Boolean value) {
        if (raw == OptionalBoolean.TRUE || raw == OptionalBoolean.FALSE) {
            return SourcedValue.metaExplicit(value);
        }
        return SourcedValue.unknown(null);
    }

    private List<Field> getAllFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    private Field findDeclaredField(Class<?> entityClass, String fieldName) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private <A extends Annotation> A findAnnotation(Field field, Class<A> annotationType) {
        return field.getAnnotation(annotationType);
    }

    private Boolean toBoolean(OptionalBoolean value) {
        if (value == OptionalBoolean.TRUE) {
            return Boolean.TRUE;
        }
        if (value == OptionalBoolean.FALSE) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class TypedDefaultValue {
        private final String rawValue;
        private final TypedValueType valueType;
        private final Object typedValue;
        private final SourcedValue<TypedValueType> valueTypeSource;
        private final SourcedValue<Object> typedValueSource;

        private TypedDefaultValue(String rawValue, TypedValueType valueType, Object typedValue) {
            this(rawValue, valueType, typedValue, SourcedValue.unknown(valueType), SourcedValue.unknown(typedValue));
        }

        private TypedDefaultValue(
            String rawValue,
            TypedValueType valueType,
            Object typedValue,
            SourcedValue<TypedValueType> valueTypeSource,
            SourcedValue<Object> typedValueSource
        ) {
            this.rawValue = rawValue;
            this.valueType = valueType;
            this.typedValue = typedValue;
            this.valueTypeSource = valueTypeSource;
            this.typedValueSource = typedValueSource;
        }
    }
}
