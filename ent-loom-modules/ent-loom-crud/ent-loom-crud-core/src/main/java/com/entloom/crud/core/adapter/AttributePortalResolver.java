package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Locale;

/** 基于治理后 Spec attributes 的 portal 解析器。 */
public final class AttributePortalResolver implements PortalResolver {
    @Override
    public String resolvePortal(BaseSpec spec) {
        Object raw = spec == null ? null : spec.getAttributes().get(ATTRIBUTE_KEY);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return null;
        }
        return String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }
}
