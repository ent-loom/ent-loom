package com.entloom.meta.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Avoids registering empty adapters that would take over CRUD's reflective fallback.
 */
class EntityClassNamesPresentCondition implements Condition {
    private static final String LIST_PROPERTY = "ent.loom.meta.entity-class-names";
    private static final String INDEXED_PROPERTY = "ent.loom.meta.entity-class-names[0]";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return hasText(environment.getProperty(LIST_PROPERTY)) || hasText(environment.getProperty(INDEXED_PROPERTY));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
