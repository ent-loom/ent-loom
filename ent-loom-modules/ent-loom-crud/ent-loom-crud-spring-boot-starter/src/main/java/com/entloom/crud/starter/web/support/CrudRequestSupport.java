package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.entloom.crud.starter.web.registry.ExposedViewTypeRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * HTTP 请求归一化与公共解析支持。
 */
@RequiredArgsConstructor
public class CrudRequestSupport {
    /** 暴露实体注册表。 */
    private final ExposedEntityRegistry exposureRegistry;
    /** viewType 注册表。 */
    private final ExposedViewTypeRegistry exposedViewTypeRegistry;

    public List<String> normalizeEntityCodes(String routeEntity, List<String> entityCodes) {
        List<String> source = entityCodes == null || entityCodes.isEmpty()
            ? Collections.singletonList(routeEntity)
            : entityCodes;
        String canonicalRouteEntity = exposureRegistry.canonicalCode(routeEntity);
        List<String> normalized = new ArrayList<String>(source.size());
        for (String code : source) {
            normalized.add(exposureRegistry.canonicalCode(code));
        }
        if (!canonicalRouteEntity.equals(normalized.get(0))) {
            throw new CrudException(CrudErrorCode.ENTITY_SCOPE_ILLEGAL, "entityCodes 首项必须等于路由实体");
        }
        return normalized;
    }

    public List<Class<?>> resolveEntityClasses(List<String> entityCodes, String routeEntity) {
        List<String> source = entityCodes == null || entityCodes.isEmpty() ? Collections.singletonList(routeEntity) : entityCodes;
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (String code : source) {
            classes.add(exposureRegistry.resolveOrThrow(code));
        }
        return classes;
    }

    public SubjectContext resolveSubject(SubjectContext subject) {
        return subject;
    }

    public String normalizeScene(String scene) {
        if (scene == null) {
            return null;
        }
        String normalized = scene.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public String resolveRequestId(String requestId) {
        if (requestId == null) {
            return null;
        }
        String normalized = requestId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public Class<?> resolveViewTypeOrThrow(String viewType) {
        return exposedViewTypeRegistry.resolveOrThrow(viewType);
    }
}
