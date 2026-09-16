package com.entloom.crud.core.repository;

/**
 * 单个实体批量更新项。
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public final class EntityUpdate<T, ID> {
    /** 目标主键。 */
    private final ID id;
    /** 局部更新内容。 */
    private final EntityPatch<T> patch;
    /** 期望版本号，可为空。 */
    private final Long expectedVersion;

    public EntityUpdate(ID id, EntityPatch<T> patch) {
        this(id, patch, null);
    }

    public EntityUpdate(ID id, EntityPatch<T> patch, Long expectedVersion) {
        this.id = id;
        this.patch = patch;
        this.expectedVersion = expectedVersion;
    }

    public ID getId() {
        return id;
    }

    public EntityPatch<T> getPatch() {
        return patch;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
