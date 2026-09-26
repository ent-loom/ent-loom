package com.entloom.base.util.reflect;

import com.entloom.base.common.OptionalBoolean;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/** 框架无关的属性类型及单列映射资格。 */
public final class EntityProperty {
    private final Field field;
    private final OptionalBoolean persistence;

    EntityProperty(Field field, OptionalBoolean persistence) {
        this.field = field;
        this.persistence = persistence;
    }

    public Field field() {
        return field;
    }

    /** 显式持久化意图，UNSET 表示按类型与修饰符推断。 */
    public OptionalBoolean persistence() {
        return persistence;
    }

    public boolean persisted() {
        return persisted(persistence);
    }

    /** 模块覆盖意图后仍须验证是否具备单列转换能力。 */
    public boolean persisted(OptionalBoolean intent) {
        if (intent == OptionalBoolean.UNSET) {
            intent = persistence;
        }
        if (intent == OptionalBoolean.FALSE) {
            return false;
        }
        boolean supported = isSingleColumnType(field.getType())
            && !Modifier.isTransient(field.getModifiers());
        if (intent == OptionalBoolean.TRUE && !supported) {
            throw new IllegalArgumentException("属性不支持单列持久化: "
                + field.getDeclaringClass().getName() + "#" + field.getName()
                + " (" + field.getType().getTypeName() + ")");
        }
        return supported;
    }

    /** 复杂对象保留业务元数据，但不参与普通值类型的业务种类推断。 */
    public boolean structured() {
        return !isSingleColumnType(field.getType());
    }

    /** 无法确定的泛型元素保留为 null，调用者不得猜测目标类型。 */
    public Class<?> elementType() {
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }
        if (!Collection.class.isAssignableFrom(field.getType())
            && !Map.class.isAssignableFrom(field.getType())) {
            return null;
        }
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) {
            return null;
        }
        Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
        if (arguments.length != (Map.class.isAssignableFrom(field.getType()) ? 2 : 1)) {
            return null;
        }
        Type element = arguments[Map.class.isAssignableFrom(field.getType()) ? 1 : 0];
        return element instanceof Class<?> ? (Class<?>) element : null;
    }

    /** 默认 JDBC 与 MySQL DDL 共同支持的明确类型集合。 */
    public static boolean isSingleColumnType(Class<?> type) {
        return type != null && (type.isEnum()
            || type == String.class || type == Character.class || type == char.class
            || type == Boolean.class || type == boolean.class
            || type == Byte.class || type == byte.class
            || type == Short.class || type == short.class
            || type == Integer.class || type == int.class
            || type == Long.class || type == long.class
            || type == Float.class || type == float.class
            || type == Double.class || type == double.class
            || type == BigDecimal.class || type == BigInteger.class
            || type == LocalDate.class || type == LocalDateTime.class || type == LocalTime.class
            || type == Instant.class || type == Date.class
            || type == java.sql.Date.class || type == java.sql.Time.class || type == java.sql.Timestamp.class
            || type == byte[].class);
    }
}
