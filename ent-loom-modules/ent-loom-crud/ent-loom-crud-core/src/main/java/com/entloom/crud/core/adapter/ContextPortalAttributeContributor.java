package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeContributor;
import com.entloom.crud.core.runtime.spec.CrudSpecReservedAttributeKeyProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** 从服务端调用上下文注入可信 portal。 */
public final class ContextPortalAttributeContributor
    implements CrudSpecAttributeContributor, CrudSpecReservedAttributeKeyProvider {

    @Override
    public Map<String, Object> contribute(BaseSpec spec) {
        Object value = CrudRequestContextHolder.getAttribute(PortalResolver.ATTRIBUTE_KEY);
        if (value == null) {
            return Collections.emptyMap();
        }
        return Collections.<String, Object>singletonMap(PortalResolver.ATTRIBUTE_KEY, value);
    }

    @Override
    public Collection<String> reservedAttributeKeys() {
        return Collections.singleton(PortalResolver.ATTRIBUTE_KEY);
    }
}
