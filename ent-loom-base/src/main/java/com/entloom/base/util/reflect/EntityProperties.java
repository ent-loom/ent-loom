package com.entloom.base.util.reflect;

import com.entloom.base.common.OptionalBoolean;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 实例属性的统一发现与可选注解读取，不引入上层模块依赖。 */
public final class EntityProperties {
    private EntityProperties() {
    }

    /** 子类属性优先；静态与编译器合成属性不进入业务模型。 */
    public static List<Field> fields(Class<?> type) {
        Map<String, Field> fields = new LinkedHashMap<String, Field>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()
                    && !fields.containsKey(field.getName())) {
                    fields.put(field.getName(), field);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<Field>(fields.values()));
    }

    public static EntityProperty describe(Field field) {
        return new EntityProperty(field,
            optionalBoolean(field, "com.entloom.meta.annotations.EntField", "persisted"));
    }

    /** Module-only 仍可读取已出现的公共意图，无需强制依赖 Meta 注解模块。 */
    public static OptionalBoolean optionalBoolean(Field field, String annotationName, String attribute) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotationName.equals(annotation.annotationType().getName())) {
                try {
                    return (OptionalBoolean) annotation.annotationType().getMethod(attribute).invoke(annotation);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalArgumentException("无法读取属性注解: " + field.getDeclaringClass().getName()
                        + "#" + field.getName() + "." + attribute, exception);
                }
            }
        }
        return OptionalBoolean.UNSET;
    }
}
