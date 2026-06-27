package com.entloom.crud.starter.web.registry;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 实体暴露注册表。
 * 对外正式 entity code 统一来自 ResourceDescriptor.resourceCode。
 */
public class ExposedEntityRegistry {
    /** 元数据注册表。 */
    private final EntityMetaRegistry entityMetaRegistry;
    /** 实体编码映射。 */
    private final Map<String, Class<?>> entityMapping = new HashMap<>();
    /** 允许暴露的实体集合。 */
    private final Set<String> includeEntities = new HashSet<>();

    public ExposedEntityRegistry() {
        this(null);
    }

    public ExposedEntityRegistry(EntityMetaRegistry entityMetaRegistry) {
        this.entityMetaRegistry = entityMetaRegistry;
    }

    /**
     * 以资源描述注册实体类型。
     *
     * @param entityType 实体类型
     */
    public void register(Class<?> entityType) {
        ResourceDescriptor descriptor = resourceDescriptor(entityType);
        registerDescriptor(descriptor, entityType);
    }

    /**
     * 注册实体编码与类型。
     *
     * @param code 实体编码
     * @param entityType 实体类型
     */
    public void register(String code, Class<?> entityType) {
        entityMapping.put(normalize(code), entityType);
    }

    /**
     * 以实体类名注册并加入白名单。
     *
     * @param entityType 实体类型
     */
    public void expose(Class<?> entityType) {
        register(entityType);
        includeEntities.add(normalize(entityCode(entityType)));
    }

    /**
     * 以指定编码注册并加入白名单。
     *
     * @param code 实体编码
     * @param entityType 实体类型
     */
    public void expose(String code, Class<?> entityType) {
        register(code, entityType);
        includeEntities.add(normalize(code));
    }

    /**
     * 设置白名单。
     *
     * @param includes 允许暴露实体列表
     */
    public void setIncludeEntities(Set<String> includes) {
        includeEntities.clear();
        if (includes != null) {
            includes.forEach(code -> includeEntities.add(normalize(code)));
        }
    }

    /**
     * 按编码解析实体类型。
     *
     * @param code 实体编码
     * @return 实体类型
     */
    /**
     * 解析实体编码并在缺失时抛出异常。
     */
    public Class<?> resolveOrThrow(String code) {
        String normalized = normalize(code);
        Class<?> type = entityMapping.get(normalized);
        if (type == null) {
            throw new CrudException(CrudErrorCode.TYPE_RESOLUTION_FAILED, "未找到实体类型: " + code);
        }
        if (!includeEntities.isEmpty()
            && !includeEntities.contains(normalized)
            && !includeEntities.contains(normalize(entityCode(type)))) {
            throw new CrudException(CrudErrorCode.ENTITY_NOT_EXPOSED, "实体未暴露: " + code);
        }
        return type;
    }

    /**
     * 将任意已注册编码解析为官方资源编码。
     */
    public String canonicalCode(String code) {
        return entityCode(resolveOrThrow(code));
    }

    /**
     * 校验实体是否允许暴露。
     *
     * @param code 实体编码
     */
    public void assertExposed(String code) {
        resolveOrThrow(code);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    private String entityCode(Class<?> entityType) {
        return resourceDescriptor(entityType).getResourceCode();
    }

    private ResourceDescriptor resourceDescriptor(Class<?> entityType) {
        if (entityType == null) {
            throw new CrudException(CrudErrorCode.TYPE_RESOLUTION_FAILED, "实体类型不能为空");
        }
        if (entityMetaRegistry == null) {
            return new ResourceDescriptor(entityType, entityType.getSimpleName(), null, Collections.<String>emptyList());
        }
        return entityMetaRegistry.getResourceDescriptor(entityType);
    }

    private void registerDescriptor(ResourceDescriptor descriptor, Class<?> entityType) {
        registerCode(descriptor.getResourceCode(), entityType);
        for (String alias : descriptor.getAliases()) {
            registerCode(alias, entityType);
        }
    }

    private void registerCode(String code, Class<?> entityType) {
        String normalized = normalize(code);
        if (!normalized.isEmpty()) {
            entityMapping.put(normalized, entityType);
        }
    }
}
