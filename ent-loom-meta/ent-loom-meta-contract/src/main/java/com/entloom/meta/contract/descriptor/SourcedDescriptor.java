package com.entloom.meta.contract.descriptor;

import com.entloom.meta.contract.value.SourcedValue;
import java.util.Collections;
import java.util.Map;

/**
 * 可追踪属性来源的 Descriptor。
 */
public interface SourcedDescriptor {
    /**
     * Descriptor 属性来源映射，key 使用 Descriptor 属性名。
     */
    default Map<String, SourcedValue<?>> sourcedValues() {
        return Collections.emptyMap();
    }

    /**
     * 获取指定属性的来源值。
     */
    default SourcedValue<?> sourcedValue(String property) {
        if (property == null) {
            return null;
        }
        return sourcedValues().get(property);
    }
}
