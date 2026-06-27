package com.entloom.meta.starter;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Avoids registering empty adapters that would take over CRUD's reflective fallback.
 */
class EntityClassesConfiguredCondition implements Condition {
    private static final String ENTITY_CLASS_NAMES = "ent.loom.meta.entity-class-names";
    private static final String ENTITY_CLASS_NAMES_INDEXED = "ent.loom.meta.entity-class-names[0]";
    private static final String BASE_PACKAGES = "ent.loom.meta.base-packages";
    private static final String BASE_PACKAGES_INDEXED = "ent.loom.meta.base-packages[0]";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return hasText(environment.getProperty(ENTITY_CLASS_NAMES))
            || hasText(environment.getProperty(ENTITY_CLASS_NAMES_INDEXED))
            || hasText(environment.getProperty(BASE_PACKAGES))
            || hasText(environment.getProperty(BASE_PACKAGES_INDEXED));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
