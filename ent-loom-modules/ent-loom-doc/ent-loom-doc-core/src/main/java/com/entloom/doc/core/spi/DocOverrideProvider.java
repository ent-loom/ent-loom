package com.entloom.doc.core.spi;

/**
 * Business extension point for DOC presentation overrides.
 */
public interface DocOverrideProvider {
    DocEntityOverride overrideFor(Class<?> entityClass, String resourceCode);

    static DocOverrideProvider noop() {
        return new DocOverrideProvider() {
            @Override
            public DocEntityOverride overrideFor(Class<?> entityClass, String resourceCode) {
                return null;
            }
        };
    }
}
