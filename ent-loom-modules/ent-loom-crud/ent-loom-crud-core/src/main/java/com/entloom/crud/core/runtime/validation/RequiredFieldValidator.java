package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 基于 CRUD 运行时元数据校验创建输入契约字段。
 */
public final class RequiredFieldValidator {
    private final CrudInputContract inputContract;

    public RequiredFieldValidator() {
        this(CrudInputContract.empty());
    }

    public RequiredFieldValidator(CrudInputContract inputContract) {
        this.inputContract = inputContract == null ? CrudInputContract.empty() : inputContract;
    }

    /** 校验创建实体；应在默认值、治理字段和业务准备前调用。 */
    public void validateCreateEntity(Object entity, EntityMeta meta) {
        validateCreateEntity(entity, meta, null);
    }

    /**
     * 校验创建实体。
     *
     * <p>当请求来自 Map/CrudRecord 时，presentFields 用于区分 primitive
     * 字段的“未传入”和显式传入的 false/0；直接传入实体实例时可传 null，
     * 此时仅校验实体最终值。</p>
     */
    public void validateCreateEntity(Object entity, EntityMeta meta, Set<String> presentFields) {
        if (entity == null || meta == null) {
            return;
        }
        Set<String> requiredFields = requiredFields(meta);
        for (String fieldName : requiredFields) {
            EntityFieldMeta fieldMeta = requireField(meta, fieldName);
            if (isPrimitiveMissing(fieldMeta, presentFields)) {
                validate(fieldMeta, null);
            } else {
                validate(fieldMeta, readField(entity, fieldName));
            }
        }
    }

    /** 校验创建值集合；缺少键和值为 null 都视为未满足必填约束。 */
    public void validateCreateValues(Map<String, ?> values, EntityMeta meta) {
        validateCreateValues(values, meta, null);
    }

    /**
     * 校验创建值集合，并允许调用方提供已从命令上下文解析出的主键。
     *
     * <p>JDBC 默认写入会把主键从 values 移到 WriteCommand.id，校验时仍需
     * 使用该已解析值，避免显式主键被误判为缺失。</p>
     */
    public void validateCreateValues(Map<String, ?> values, EntityMeta meta, Object resolvedId) {
        if (values == null || meta == null) {
            return;
        }
        Set<String> requiredFields = requiredFields(meta);
        for (String fieldName : requiredFields) {
            EntityFieldMeta fieldMeta = requireField(meta, fieldName);
            Object value = values.get(fieldName);
            if (fieldName.equals(meta.getIdField()) && resolvedId != null) {
                value = resolvedId;
            }
            validate(fieldMeta, value);
        }
    }

    private Set<String> requiredFields(EntityMeta meta) {
        Set<String> fields = inputContract.resolveCreateRequiredFields(meta);
        for (String fieldName : fields) {
            requireField(meta, fieldName);
        }
        return fields;
    }

    private EntityFieldMeta requireField(EntityMeta meta, String fieldName) {
        EntityFieldMeta field = meta.resolveFieldMeta(fieldName);
        if (field == null) {
            throw new ValidationException("创建契约字段不存在: " + meta.getEntityName() + "." + fieldName);
        }
        if (!field.isWritable()) {
            throw new ValidationException("创建契约字段不可写: " + meta.getEntityName() + "." + fieldName);
        }
        if (fieldName.equals(meta.getIdField()) && meta.getIdPolicy() == EntityIdPolicy.GENERATED) {
            throw new ValidationException("生成主键不可配置为创建输入字段: " + meta.getEntityName() + "." + fieldName);
        }
        return field;
    }

    private boolean isPrimitiveMissing(EntityFieldMeta field, Set<String> presentFields) {
        return presentFields != null
            && field.getJavaType() != null
            && field.getJavaType().isPrimitive()
            && !presentFields.contains(field.getFieldName());
    }

    private void validate(EntityFieldMeta field, Object value) {
        if (isPresent(value)) {
            return;
        }
        String label = field.getLabel() == null ? field.getFieldName() : field.getLabel();
        throw new ValidationException(label + "不能为空");
    }

    private boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence) {
            return !value.toString().trim().isEmpty();
        }
        if (value instanceof Collection<?>) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map<?, ?>) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return !value.getClass().isArray() || Array.getLength(value) > 0;
    }

    private Object readField(Object entity, String fieldName) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new ValidationException("无法读取必填字段: " + fieldName);
            }
        }
        throw new ValidationException("必填字段不存在: " + fieldName);
    }
}
