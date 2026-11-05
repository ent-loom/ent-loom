package com.entloom.crud.starter.web.support;

import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeContributor;
import com.entloom.crud.core.runtime.spec.CrudSpecReservedAttributeKeyProvider;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将当前 HTTP 请求标识传给执行规格及审计。 */
public final class CrudHttpRequestAttributeContributor
    implements CrudSpecAttributeContributor, CrudSpecReservedAttributeKeyProvider {

    @Override
    public Map<String, Object> contribute(BaseSpec spec) {
        CrudHttpRequestContext context = CrudHttpRequestContext.current();
        if (context == null) {
            if (spec == null) return Collections.emptyMap();
            Map<String, Object> attributes = new LinkedHashMap<>();
            Map<String, Object> source = spec.getAttributes();
            if (source.get("requestId") != null) attributes.put("requestId", source.get("requestId"));
            if (source.get("traceId") != null) attributes.put("traceId", source.get("traceId"));
            return attributes;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("requestId", context.getRequestId());
        if (context.getTraceId() != null) attributes.put("traceId", context.getTraceId());
        return attributes;
    }

    @Override
    public Collection<String> reservedAttributeKeys() {
        return Arrays.asList("requestId", "traceId");
    }
}
