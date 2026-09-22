package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.validation.RequiredFieldValidator;
import com.entloom.crud.core.runtime.validation.CreateDefaultValueApplier;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 普通实体 CREATE 场景的强类型模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractEntityCreateHandler<T, R>
    extends AbstractEntityCommandHandler<T, R> {
    private final CrudInputContract inputContract;
    private final CreateDefaultValueApplier createDefaultValueApplier = new CreateDefaultValueApplier();

    protected AbstractEntityCreateHandler() {
        this(CrudInputContract.empty());
    }

    protected AbstractEntityCreateHandler(CrudInputContract inputContract) {
        this.inputContract = inputContract == null ? CrudInputContract.empty() : inputContract;
    }

    @Override
    public final CommandOperation operation() {
        return CommandOperation.CREATE;
    }

    @Override
    protected void beforeHandleEntity(
        T requested,
        CommandSpec<Object> spec,
        EntityMeta meta,
        Set<String> presentFields
    ) {
        Set<String> mutablePresentFields = presentFields == null
            ? new LinkedHashSet<String>()
            : presentFields;
        requiredFieldValidator().validateCreateEntity(requested, meta, mutablePresentFields);
        createDefaultValueApplier.applyToEntity(requested, meta, mutablePresentFields);
        enforceCreateScope(requested, spec, meta, mutablePresentFields);
        beforeHandleEntity(requested, spec, meta);
        prepareCreateEntity(requested, spec, meta, mutablePresentFields);
    }

    /** 返回当前处理器使用的外部输入契约，Spring 子类可通过注入覆盖。 */
    protected CrudInputContract inputContract() {
        return inputContract;
    }

    private RequiredFieldValidator requiredFieldValidator() {
        return new RequiredFieldValidator(inputContract());
    }

    /** 兼容旧版三参数扩展钩子；默认不执行额外逻辑。 */
    @Override
    protected void beforeHandleEntity(T requested, CommandSpec<Object> spec, EntityMeta meta) {
    }

    /**
     * 在输入契约校验、默认值补齐和治理范围注入后准备业务计算字段。
     *
     * <p>输入契约只判断请求是否满足外部输入边界；最终实体值和跨字段规则由业务处理负责。</p>
     */
    protected void prepareCreateEntity(
        T requested,
        CommandSpec<Object> spec,
        EntityMeta meta,
        Set<String> presentFields
    ) {
        prepareCreateEntity(requested, spec, meta);
    }

    /** 简化扩展钩子；默认不执行额外逻辑。 */
    protected void prepareCreateEntity(T requested, CommandSpec<Object> spec, EntityMeta meta) {
    }

    private void enforceCreateScope(
        T requested,
        CommandSpec<Object> spec,
        EntityMeta meta,
        Set<String> presentFields
    ) {
        CrudDataScope scope = spec == null ? null : spec.getGovernanceScope();
        if (requested == null || meta == null || scope == null || scope.isExplicitAll()) {
            return;
        }
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String fieldName = entry.getKey();
            if (!meta.getAllowedFields().contains(fieldName)) {
                continue;
            }
            Field field = findField(requested.getClass(), fieldName);
            if (field == null) {
                continue;
            }
            Object expected = entry.getValue();
            Object current = readFieldValue(requested, field);
            boolean absentFromRequest = presentFields != null && !presentFields.contains(fieldName);
            if (absentFromRequest || current == null) {
                writeFieldValue(requested, field, injectCreateScopeValue(expected, fieldName));
                if (presentFields != null) {
                    presentFields.add(fieldName);
                }
                continue;
            }
            if (!matchesScopeValue(expected, current)) {
                throw new DataScopeDeniedException("创建载荷超出治理范围: " + fieldName);
            }
        }
    }

    private Object injectCreateScopeValue(Object expected, String fieldName) {
        if (expected instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) expected;
            if (values.size() == 1) {
                return values.iterator().next();
            }
            throw new DataScopeDeniedException("创建载荷必须显式提供范围字段: " + fieldName);
        }
        return expected;
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Object readFieldValue(Object target, Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException ex) {
            throw new DataScopeDeniedException("无法读取治理范围字段: " + field.getName());
        }
    }

    private void writeFieldValue(Object target, Field field, Object value) {
        try {
            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            if (value == null || fieldType.isInstance(value)) {
                field.set(target, value);
                return;
            }
            if (fieldType == Long.class || fieldType == Long.TYPE) {
                field.set(target, value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)));
                return;
            }
            if (fieldType == Integer.class || fieldType == Integer.TYPE) {
                field.set(target, value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value)));
                return;
            }
            if (fieldType == String.class) {
                field.set(target, String.valueOf(value));
                return;
            }
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            throw new DataScopeDeniedException("无法写入治理范围字段: " + field.getName());
        } catch (RuntimeException ex) {
            if (ex instanceof DataScopeDeniedException) {
                throw ex;
            }
            throw new DataScopeDeniedException("治理范围字段类型不匹配: " + field.getName());
        }
    }

    private boolean matchesScopeValue(Object expected, Object actual) {
        if (expected instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) expected;
            for (Object value : values) {
                if (matchesSingleValue(value, actual)) {
                    return true;
                }
            }
            return false;
        }
        return matchesSingleValue(expected, actual);
    }

    private boolean matchesSingleValue(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number && actual instanceof Number) {
            return numberValueEquals((Number) expected, (Number) actual);
        }
        return expected.equals(actual);
    }

    private boolean numberValueEquals(Number expected, Number actual) {
        if (isNonFinite(expected) || isNonFinite(actual)) {
            return Double.compare(expected.doubleValue(), actual.doubleValue()) == 0;
        }
        return toBigDecimal(expected).compareTo(toBigDecimal(actual)) == 0;
    }

    private boolean isNonFinite(Number value) {
        if (value instanceof Double) {
            double d = value.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d);
        }
        if (value instanceof Float) {
            float f = value.floatValue();
            return Float.isNaN(f) || Float.isInfinite(f);
        }
        return false;
    }

    private BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            return BigDecimal.valueOf(value.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
