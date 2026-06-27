package com.entloom.crud.core.capability.importing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 导入载荷定制器注册表。
 */
public class ImportPayloadCustomizerRegistry {
    private final List<ImportPayloadCustomizer> customizers;

    public ImportPayloadCustomizerRegistry(Collection<ImportPayloadCustomizer> customizers) {
        List<ImportPayloadCustomizer> values = new ArrayList<ImportPayloadCustomizer>();
        if (customizers != null) {
            for (ImportPayloadCustomizer customizer : customizers) {
                if (customizer != null) {
                    values.add(customizer);
                }
            }
        }
        this.customizers = Collections.unmodifiableList(values);
    }

    public ImportSpec customize(ImportSpec spec) {
        ImportSpec current = spec;
        for (ImportPayloadCustomizer customizer : customizers) {
            if (customizer.supports(current)) {
                current = Objects.requireNonNull(customizer.customize(current), "ImportPayloadCustomizer 不能返回 null");
            }
        }
        return current;
    }

    public List<ImportPayloadCustomizer> customizers() {
        return new ArrayList<ImportPayloadCustomizer>(customizers);
    }
}
