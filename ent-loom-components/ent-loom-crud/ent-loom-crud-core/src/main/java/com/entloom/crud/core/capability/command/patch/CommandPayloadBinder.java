package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Map;
import java.util.Set;

/**
 * 命令 payload 绑定器。
 */
public interface CommandPayloadBinder {
    <T> EntityPatch<T> bindEntityPatch(Object payload, Class<T> entityType, EntityMeta meta);

    default <T> EntityPatch<T> bindEntityPatch(
        Object payload,
        Class<T> entityType,
        EntityMeta meta,
        Set<String> additionalPresentFields
    ) {
        return bindEntityPatch(payload, entityType, meta);
    }

    <T> T bindEntity(Object payload, Class<T> entityType, EntityMeta meta);

    default <T> T bindEntity(Object payload, Class<T> entityType, EntityMeta meta, Set<String> additionalEntityFields) {
        return bindEntity(payload, entityType, meta);
    }

    Map<String, Object> bindFieldMap(Object payload, EntityMeta meta);
}
