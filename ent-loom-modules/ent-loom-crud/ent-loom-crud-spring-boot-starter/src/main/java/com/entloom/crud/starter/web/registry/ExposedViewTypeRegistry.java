package com.entloom.crud.starter.web.registry;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外暴露的 viewType 注册表。
 */
public class ExposedViewTypeRegistry {
    private final Map<String, Class<?>> typeMapping = new LinkedHashMap<String, Class<?>>();

    public void register(Class<?> viewType) {
        if (viewType == null) {
            return;
        }
        register(viewType.getSimpleName(), viewType);
        register(viewType.getName(), viewType);
    }

    public void register(String code, Class<?> viewType) {
        if (code == null || code.trim().isEmpty() || viewType == null) {
            return;
        }
        typeMapping.put(normalize(code), viewType);
    }

    public Class<?> resolveOrThrow(String code) {
        Class<?> viewType = typeMapping.get(normalize(code));
        if (viewType == null) {
            throw new CrudException(CrudErrorCode.TYPE_RESOLUTION_FAILED, "未找到 viewType: " + code);
        }
        return viewType;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim();
    }
}
