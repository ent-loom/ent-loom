package com.entloom.meta.starter;

import com.entloom.doc.core.spi.DocEntityMetaResolver;

/**
 * Default DOC resolver used only when business code does not provide one.
 */
public class DefaultDocEntityMetaResolver implements DocEntityMetaResolver {

    @Override
    public String resolveTableName(Class<?> entityClass, String configuredTableName) {
        String configured = EntLoomMetaProperties.trimToNull(configuredTableName);
        if (configured != null) {
            return configured;
        }
        return entityClass == null ? null : camelToSnake(entityClass.getSimpleName());
    }

    @Override
    public String resolveColumn(Class<?> entityClass, String property) {
        String normalized = EntLoomMetaProperties.trimToNull(property);
        return normalized == null ? null : camelToSnake(normalized);
    }

    private static String camelToSnake(String value) {
        String normalized = EntLoomMetaProperties.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(normalized.length() + 8);
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
