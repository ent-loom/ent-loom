package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeContributor;
import com.entloom.crud.core.runtime.spec.CrudSpecReservedAttributeKeyProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 从服务端调用上下文注入 accessEntry。
 */
public class ContextAccessEntryAttributeContributor
    implements CrudSpecAttributeContributor, CrudSpecReservedAttributeKeyProvider {
    private final String attributeKey;

    public ContextAccessEntryAttributeContributor() {
        this(AccessEntryResolver.ATTRIBUTE_KEY);
    }

    public ContextAccessEntryAttributeContributor(String attributeKey) {
        if (attributeKey == null || attributeKey.trim().isEmpty()) {
            throw new IllegalArgumentException("attributeKey 不能为空");
        }
        this.attributeKey = attributeKey.trim();
    }

    @Override
    public Map<String, Object> contribute(BaseSpec spec) {
        Object value = CrudRequestContextHolder.getAttribute(attributeKey);
        if (value == null) {
            return Collections.emptyMap();
        }
        return Collections.<String, Object>singletonMap(attributeKey, value);
    }

    @Override
    public Collection<String> reservedAttributeKeys() {
        return Collections.singleton(attributeKey);
    }
}
