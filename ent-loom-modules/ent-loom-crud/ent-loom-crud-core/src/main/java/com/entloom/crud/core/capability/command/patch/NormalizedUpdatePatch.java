package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DAO 使用的内部字段变更模型。
 *
 * <p>旧 Patch 的 delegate Map 只在本类这一处转换，避免 DAO 依赖历史透传视图。</p>
 */
public final class NormalizedUpdatePatch {
    private final Object id;
    private final Map<String, Object> changes;

    private NormalizedUpdatePatch(Object id, Map<String, Object> changes) {
        this.id = id;
        this.changes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(changes));
    }

    public static <T> NormalizedUpdatePatch from(UpdatePatch<T> patch, EntityMeta meta) {
        if (patch == null || meta == null) {
            throw new ValidationException("UpdatePatch 和 EntityMeta 不能为空");
        }
        if (patch.getEntityType() == null || !patch.getEntityType().equals(meta.getEntityType())) {
            throw new ValidationException("UpdatePatch 实体类型与元数据不一致");
        }
        if (patch.getId() == null) {
            throw new ValidationException("UpdatePatch 主键不能为空");
        }
        if (patch.getPresentFields() == null || patch.getPersistableFields() == null
            || patch.getValuesForDelegate() == null) {
            throw new ValidationException("UpdatePatch 字段集合不能为空");
        }
        Map<String, Object> source = patch.getValuesForDelegate();
        if (!source.keySet().equals(patch.getPersistableFields())) {
            throw new ValidationException("UpdatePatch values 必须与 persistableFields 完全一致");
        }
        if (!patch.getPresentFields().containsAll(patch.getPersistableFields())) {
            throw new ValidationException("UpdatePatch persistableFields 必须来自 presentFields");
        }
        if (source.isEmpty()) {
            throw new ValidationException("UpdatePatch 不能为空");
        }
        LinkedHashMap<String, Object> changes = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String field = entry.getKey();
            validateWritableField(meta, field);
            changes.put(field, entry.getValue());
        }
        return new NormalizedUpdatePatch(patch.getId(), changes);
    }

    private static void validateWritableField(EntityMeta meta, String field) {
        if (field == null || field.equals(meta.getIdField()) || field.equals(meta.getLogicDeleteField())
            || meta.getAllowedFields().contains(field) == false) {
            throw new ValidationException("不允许更新字段: " + field);
        }
        EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
        if (fieldMeta == null || fieldMeta.isRelation() || !fieldMeta.isWritable()
            || fieldMeta.isImmutable() || fieldMeta.isScopeField()) {
            throw new ValidationException("不允许更新字段: " + field);
        }
    }

    public Object getId() {
        return id;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
