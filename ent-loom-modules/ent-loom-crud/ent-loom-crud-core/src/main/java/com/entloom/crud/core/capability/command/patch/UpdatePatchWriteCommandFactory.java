package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 将可信校验后的局部更新转换为结构化写命令。 */
public final class UpdatePatchWriteCommandFactory {
    private UpdatePatchWriteCommandFactory() {
    }

    public static <T> WriteCommand<Map<String, Object>> create(UpdatePatch<T> patch, EntityMeta meta) {
        if (patch == null || meta == null) {
            throw new ValidationException("UpdatePatch 和 EntityMeta 不能为空");
        }
        if (patch.getEntityType() == null || !patch.getEntityType().equals(meta.getEntityType())) {
            throw new ValidationException("UpdatePatch 实体类型与元数据不一致");
        }
        Map<String, Object> values = patch.getValuesForDelegate();
        Set<String> presentFields = patch.getPresentFields();
        Set<String> persistableFields = patch.getPersistableFields();
        if (values == null || presentFields == null || persistableFields == null) {
            throw new ValidationException("UpdatePatch 字段集合不能为空");
        }
        if (!values.keySet().equals(persistableFields)) {
            throw new ValidationException("UpdatePatch values 必须与 persistableFields 完全一致");
        }
        if (!presentFields.containsAll(persistableFields)) {
            throw new ValidationException("UpdatePatch persistableFields 必须来自 presentFields");
        }
        for (String field : persistableFields) {
            validateWritableField(meta, field);
        }
        return new WriteCommand<Map<String, Object>>(
            CommandOperation.UPDATE,
            patch.getId(),
            new LinkedHashMap<String, Object>(values)
        );
    }

    private static void validateWritableField(EntityMeta meta, String field) {
        if (field == null || field.equals(meta.getIdField()) || !meta.getAllowedFields().contains(field)) {
            throw new ValidationException("不允许更新字段: " + field);
        }
        EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
        if (fieldMeta == null || fieldMeta.isRelation() || !fieldMeta.isWritable() || fieldMeta.isImmutable()) {
            throw new ValidationException("不允许更新字段: " + field);
        }
    }
}
