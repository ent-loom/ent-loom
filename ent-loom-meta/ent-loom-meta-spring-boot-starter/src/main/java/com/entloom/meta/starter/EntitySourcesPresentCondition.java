package com.entloom.meta.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 仅在显式配置实体来源时装配适配器。
 */
class EntitySourcesPresentCondition implements Condition {
    private static final String LIST_PROPERTY = "ent.loom.meta.entity-class-names";
    private static final String INDEXED_PROPERTY = "ent.loom.meta.entity-class-names[0]";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return hasText(environment.getProperty(LIST_PROPERTY)) || hasText(environment.getProperty(INDEXED_PROPERTY))
            || hasText(environment.getProperty("ent.loom.meta.base-packages"))
            || hasText(environment.getProperty("ent.loom.meta.base-packages[0]"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
