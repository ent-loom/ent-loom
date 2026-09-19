package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.contract.value.SourcedValue;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 业务必填约束解析；不引入 Validation 运行时依赖，不执行校验或填充值。 */
final class RequiredInference {
    private RequiredInference() {
    }

    static SourcedValue<Boolean> resolve(
        Field field,
        EntField meta
    ) {
        if (meta != null && meta.required() != OptionalBoolean.UNSET) {
            return SourcedValue.metaExplicit(meta.required() == OptionalBoolean.TRUE);
        }
        if (hasRequiredConstraint(field.getAnnotations())) {
            return SourcedValue.inferred(Boolean.TRUE);
        }
        // Bean Validation 同样支持属性 getter；限定同名属性，不读取其他 DTO 的约束。
        String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method getter = field.getDeclaringClass().getMethod(prefix + suffix);
                if (getter.getReturnType() == field.getType()
                    && ("get".equals(prefix) || field.getType() == boolean.class)
                    && !java.lang.reflect.Modifier.isStatic(getter.getModifiers())
                    && hasRequiredConstraint(getter.getAnnotations())) {
                    return SourcedValue.inferred(Boolean.TRUE);
                }
            } catch (NoSuchMethodException ignored) {
                // 没有公开 getter 时仅使用字段上的声明。
            }
        }
        // Java 类型、字段名和数据库非空均不能决定是否必填。
        return SourcedValue.unknown(null);
    }

    private static boolean hasRequiredConstraint(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String name = annotation.annotationType().getName();
            if (!(name.startsWith("javax.validation.constraints.")
                || name.startsWith("jakarta.validation.constraints."))) {
                continue;
            }
            String simpleName = annotation.annotationType().getSimpleName();
            if (!("NotNull".equals(simpleName) || "NotBlank".equals(simpleName) || "NotEmpty".equals(simpleName))) {
                continue;
            }
            try {
                Class<?>[] groups = (Class<?>[]) annotation.annotationType().getMethod("groups").invoke(annotation);
                if (groups.length == 0) {
                    return true;
                }
                for (Class<?> group : groups) {
                    if ("javax.validation.groups.Default".equals(group.getName())
                        || "jakarta.validation.groups.Default".equals(group.getName())) {
                        return true;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // 无法读取约束时保留未知，不能据此声明必填。
            }
        }
        return false;
    }
}
