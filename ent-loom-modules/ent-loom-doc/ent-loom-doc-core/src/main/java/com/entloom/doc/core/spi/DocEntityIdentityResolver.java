package com.entloom.doc.core.spi;

/**
 * Optional DOC SPI for resolving an entity's logical identity field.
 */
public interface DocEntityIdentityResolver extends DocEntityMetaResolver {

    /**
     * Resolve the Java property used as the entity identity field.
     */
    String resolveIdField(Class<?> entityClass);

    /**
     * Resolve an entity class by relation target code when it is not part of
     * the generated document model set.
     */
    default Class<?> resolveEntityClass(String code) {
        return null;
    }
}
