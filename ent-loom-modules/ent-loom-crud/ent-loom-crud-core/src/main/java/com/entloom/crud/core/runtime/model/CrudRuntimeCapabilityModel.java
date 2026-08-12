package com.entloom.crud.core.runtime.model;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

/**
 * CRUD 运行期能力元数据。
 */
@Getter
public final class CrudRuntimeCapabilityModel {
    private final Set<CrudOperationDomain> capabilities;
    private final Set<CrudOperationKey> operations;

    public CrudRuntimeCapabilityModel(Set<CrudOperationDomain> capabilities, Set<CrudOperationKey> operations) {
        this.capabilities = copy(capabilities);
        this.operations = operations == null
            ? Collections.<CrudOperationKey>emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<CrudOperationKey>(operations));
    }

    public static CrudRuntimeCapabilityModel empty() {
        return new CrudRuntimeCapabilityModel(null, null);
    }

    private static Set<CrudOperationDomain> copy(Set<CrudOperationDomain> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<CrudOperationDomain>(source));
    }
}
