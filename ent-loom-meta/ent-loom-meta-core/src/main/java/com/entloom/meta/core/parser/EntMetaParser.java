package com.entloom.meta.core.parser;

import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;

/**
 * Parses Ent meta annotations into the common descriptor model.
 */
public interface EntMetaParser {
    /**
     * Parse an entity class.
     *
     * @param entityClass entity class
     * @return parsed descriptor
     */
    EntEntityDescriptor parse(Class<?> entityClass);

    /**
     * Parse an entity class and return structured diagnostics.
     *
     * @param entityClass entity class
     * @return descriptor and diagnostics
     */
    default MetaDiagnosticResult<EntEntityDescriptor> parseWithDiagnostics(Class<?> entityClass) {
        return MetaDiagnosticResult.success(parse(entityClass));
    }
}
