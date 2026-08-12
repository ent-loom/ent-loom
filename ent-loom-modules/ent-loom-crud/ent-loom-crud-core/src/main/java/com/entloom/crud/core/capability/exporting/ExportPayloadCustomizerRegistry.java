package com.entloom.crud.core.capability.exporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 导出载荷定制器注册表。
 */
public class ExportPayloadCustomizerRegistry {
    private final List<ExportPayloadCustomizer> customizers;

    public ExportPayloadCustomizerRegistry(Collection<ExportPayloadCustomizer> customizers) {
        List<ExportPayloadCustomizer> values = new ArrayList<ExportPayloadCustomizer>();
        if (customizers != null) {
            for (ExportPayloadCustomizer customizer : customizers) {
                if (customizer != null) {
                    values.add(customizer);
                }
            }
        }
        this.customizers = Collections.unmodifiableList(values);
    }

    public ExportSpec customize(ExportSpec spec) {
        ExportSpec current = spec;
        for (ExportPayloadCustomizer customizer : customizers) {
            if (customizer.supports(current)) {
                current = Objects.requireNonNull(customizer.customize(current), "ExportPayloadCustomizer 不能返回 null");
            }
        }
        return current;
    }

    public List<ExportPayloadCustomizer> customizers() {
        return new ArrayList<ExportPayloadCustomizer>(customizers);
    }
}
