package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 默认导出列解析器。
 */
public class DefaultExportColumnResolver implements ExportColumnResolver {
    private static final Set<String> INTERNAL_FIELDS = new HashSet<String>(Arrays.asList(
        "tenantid",
        "orgid",
        "organizationid",
        "createdby",
        "updatedby",
        "createby",
        "updateby",
        "version"
    ));

    @Override
    public List<ExportColumn> resolve(ExportSpec spec, EntityMeta meta, RelationGraph relationGraph) {
        if (spec == null) {
            throw new ValidationException("导出请求规范(spec)不能为空");
        }
        if (meta == null) {
            throw new ValidationException("实体元数据不能为空");
        }
        boolean explicit = spec.getFields() != null && !spec.getFields().isEmpty();
        Set<String> relationForeignKeys = relationForeignKeys(meta, relationGraph);
        Set<String> configuredDisplayFields = explicit ? new LinkedHashSet<String>() : configuredDisplayFields(meta);
        List<String> candidates = explicit ? explicitCandidates(spec) : defaultCandidates(meta);
        List<ExportColumn> columns = new ArrayList<ExportColumn>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String candidate : candidates) {
            String field = normalizeField(candidate);
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (explicit) {
                validateExplicitField(meta, field, fieldMeta, relationForeignKeys, candidate);
                addColumn(columns, seen, field, fieldMeta);
            } else {
                addDefaultColumn(meta, columns, seen, field, fieldMeta, relationForeignKeys, configuredDisplayFields);
            }
        }
        if (columns.isEmpty()) {
            throw new ValidationException("导出字段不能为空");
        }
        return columns;
    }

    private List<String> explicitCandidates(ExportSpec spec) {
        return spec.getFields();
    }

    private List<String> defaultCandidates(EntityMeta meta) {
        List<String> fields = new ArrayList<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            fields.add(entry.getKey());
        }
        return fields;
    }

    private void validateExplicitField(
        EntityMeta meta,
        String field,
        EntityFieldMeta fieldMeta,
        Set<String> relationForeignKeys,
        String rawField
    ) {
        if (fieldMeta == null) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不存在: " + rawField);
        }
        if (!meta.getAllowedFields().contains(field)) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不在白名单内: " + rawField);
        }
        if (field.equals(meta.getIdField())) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不允许包含主键字段: " + rawField);
        }
        if (fieldMeta.isRelation()) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不允许包含关系对象字段: " + rawField);
        }
        if (field.equals(meta.getLogicDeleteField())) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不允许包含逻辑删除字段: " + rawField);
        }
        if (isInternalField(field)) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不允许包含内部字段: " + rawField);
        }
        if (!isExportable(fieldMeta)) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不可导出: " + rawField);
        }
        if (isDisplayOwnerField(meta, field, fieldMeta, relationForeignKeys)) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不允许包含外键 ID 字段，请选择对应展示字段: " + rawField);
        }
        if (field.indexOf('.') >= 0) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出字段不支持关系路径: " + rawField);
        }
    }

    private boolean isDefaultVisible(
        EntityMeta meta,
        String field,
        EntityFieldMeta fieldMeta,
        Set<String> relationForeignKeys
    ) {
        if (fieldMeta == null || !meta.getAllowedFields().contains(field)) {
            return false;
        }
        if (!isExportable(fieldMeta) || !isExportDefaultVisible(fieldMeta)) {
            return false;
        }
        if (field.indexOf('.') >= 0 || fieldMeta.isRelation()) {
            return false;
        }
        if (field.equals(meta.getLogicDeleteField()) || field.equals(meta.getIdField()) || isInternalField(field)) {
            return false;
        }
        if (relationForeignKeys.contains(field) && !Boolean.TRUE.equals(fieldMeta.getExportDefaultVisible())) {
            return false;
        }
        return !isConventionalForeignKeyWithDisplay(meta, field);
    }

    private void addDefaultColumn(
        EntityMeta meta,
        List<ExportColumn> columns,
        Set<String> seen,
        String field,
        EntityFieldMeta fieldMeta,
        Set<String> relationForeignKeys,
        Set<String> configuredDisplayFields
    ) {
        if (fieldMeta != null && fieldMeta.getDisplayField() != null) {
            if (!isDisplayOwnerVisible(meta, field, fieldMeta)) {
                return;
            }
            EntityFieldMeta displayMeta = requireLegalDisplayField(meta, field, fieldMeta.getDisplayField());
            addDisplayColumn(columns, seen, fieldMeta, fieldMeta.getDisplayField(), displayMeta);
            return;
        }
        if (configuredDisplayFields.contains(field)) {
            return;
        }
        if (isDefaultVisible(meta, field, fieldMeta, relationForeignKeys)) {
            addColumn(columns, seen, field, fieldMeta);
        }
    }

    private boolean isDisplayOwnerVisible(EntityMeta meta, String field, EntityFieldMeta fieldMeta) {
        return meta.getAllowedFields().contains(field)
            && isExportable(fieldMeta)
            && isExportDefaultVisible(fieldMeta)
            && field.indexOf('.') < 0
            && !fieldMeta.isRelation()
            && !field.equals(meta.getLogicDeleteField())
            && !field.equals(meta.getIdField())
            && !isInternalField(field);
    }

    private void addColumn(List<ExportColumn> columns, Set<String> seen, String field, EntityFieldMeta fieldMeta) {
        if (seen.add(field)) {
            columns.add(new ExportColumn(field, field, resolveHeader(field, fieldMeta), fieldMeta, resolveFormat(fieldMeta)));
        }
    }

    private void addDisplayColumn(
        List<ExportColumn> columns,
        Set<String> seen,
        EntityFieldMeta ownerMeta,
        String displayField,
        EntityFieldMeta displayMeta
    ) {
        if (seen.add(displayField)) {
            columns.add(new ExportColumn(
                displayField,
                displayField,
                resolveDisplayHeader(displayField, ownerMeta, displayMeta),
                displayMeta,
                firstNonBlank(ownerMeta == null ? null : ownerMeta.getExportFormat(), resolveFormat(displayMeta))
            ));
        }
    }

    private String resolveHeader(String field, EntityFieldMeta fieldMeta) {
        if (fieldMeta != null && fieldMeta.getExportLabel() != null) {
            return fieldMeta.getExportLabel();
        }
        if (fieldMeta != null && fieldMeta.getFieldName() != null && !fieldMeta.getFieldName().trim().isEmpty()) {
            return fieldMeta.getFieldName().trim();
        }
        return field;
    }

    private String resolveDisplayHeader(String displayField, EntityFieldMeta ownerMeta, EntityFieldMeta displayMeta) {
        if (ownerMeta != null && ownerMeta.getExportLabel() != null) {
            return ownerMeta.getExportLabel();
        }
        return resolveHeader(displayField, displayMeta);
    }

    private String resolveFormat(EntityFieldMeta fieldMeta) {
        return fieldMeta == null ? null : fieldMeta.getExportFormat();
    }

    private Set<String> relationForeignKeys(EntityMeta meta, RelationGraph relationGraph) {
        Set<String> fields = new LinkedHashSet<String>();
        if (relationGraph == null || meta.getEntityType() == null) {
            return fields;
        }
        for (RelationEdge edge : relationGraph.getEdges()) {
            if (edge != null
                && meta.getEntityType().equals(edge.getFromEntity())
                && edge.getFromField() != null
                && !edge.getFromField().trim().isEmpty()) {
                fields.add(edge.getFromField().trim());
            }
        }
        return fields;
    }

    private boolean isConventionalForeignKeyWithDisplay(EntityMeta meta, String field) {
        if (field == null || field.length() <= 2 || !field.endsWith("Id")) {
            return false;
        }
        String displayField = field.substring(0, field.length() - 2) + "Name";
        return isLegalDefaultDisplayField(meta, displayField);
    }

    private boolean isDisplayOwnerField(
        EntityMeta meta,
        String field,
        EntityFieldMeta fieldMeta,
        Set<String> relationForeignKeys
    ) {
        return fieldMeta.getDisplayField() != null
            || (relationForeignKeys != null && relationForeignKeys.contains(field))
            || isConventionalForeignKeyWithDisplay(meta, field);
    }

    private boolean isLegalDefaultDisplayField(EntityMeta meta, String displayField) {
        EntityFieldMeta displayMeta = meta.resolveFieldMeta(displayField);
        return displayMeta != null
            && !displayMeta.isRelation()
            && meta.getAllowedFields().contains(displayField)
            && !displayField.equals(meta.getLogicDeleteField())
            && !isInternalField(displayField)
            && isExportable(displayMeta)
            && isExportDefaultVisible(displayMeta);
    }

    private Set<String> configuredDisplayFields(EntityMeta meta) {
        Set<String> fields = new LinkedHashSet<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            EntityFieldMeta fieldMeta = entry.getValue();
            if (fieldMeta != null
                && fieldMeta.getDisplayField() != null
                && isDisplayOwnerVisible(meta, entry.getKey(), fieldMeta)
                && isLegalDefaultDisplayField(meta, fieldMeta.getDisplayField())) {
                fields.add(fieldMeta.getDisplayField());
            }
        }
        return fields;
    }

    private EntityFieldMeta requireLegalDisplayField(EntityMeta meta, String ownerField, String displayField) {
        if (displayField.indexOf('.') >= 0) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出展示字段不支持关系路径: " + ownerField + " -> " + displayField);
        }
        if (!isLegalDefaultDisplayField(meta, displayField)) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导出展示字段不可用: " + ownerField + " -> " + displayField);
        }
        return meta.resolveFieldMeta(displayField);
    }

    private boolean isExportable(EntityFieldMeta fieldMeta) {
        return fieldMeta == null || !Boolean.FALSE.equals(fieldMeta.getExportable());
    }

    private boolean isExportDefaultVisible(EntityFieldMeta fieldMeta) {
        return fieldMeta == null || !Boolean.FALSE.equals(fieldMeta.getExportDefaultVisible());
    }

    private boolean isInternalField(String field) {
        return field != null && INTERNAL_FIELDS.contains(field.toLowerCase(Locale.ROOT));
    }

    private String normalizeField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new ValidationException("字段名不能为空");
        }
        return field.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }
}
