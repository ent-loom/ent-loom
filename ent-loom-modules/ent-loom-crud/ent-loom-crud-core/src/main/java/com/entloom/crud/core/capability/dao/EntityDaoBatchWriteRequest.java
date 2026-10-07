package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 逐目标批量写入的 Core 输入合同。
 *
 * <p>本合同只冻结授权绑定、主键绑定、操作形状和版本条件，不代表存储适配器已经具备
 * 事务、影响行数、回滚或审计聚合能力。默认原子性固定为全量原子，适配器不满足时必须
 * 在 SQL 前拒绝。</p>
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public final class EntityDaoBatchWriteRequest<T, ID> {
    private final EntityDaoBatchAuthorizationContext authorization;
    private final List<Item<T, ID>> items;

    private EntityDaoBatchWriteRequest(
        EntityDaoBatchAuthorizationContext authorization,
        Collection<? extends Item<T, ID>> source
    ) {
        this.authorization = Objects.requireNonNull(authorization, "批量授权上下文不能为空");
        Objects.requireNonNull(source, "批量写入项不能为空");
        if (source.size() != authorization.size()) {
            throw new ValidationException("批量写入项必须逐项对应授权目标");
        }
        List<Item<T, ID>> copy = new ArrayList<Item<T, ID>>();
        List<String> targetIds = authorization.getTargetIds();
        EntityDaoBatchWriteOperation operation = null;
        int index = 0;
        for (Item<T, ID> item : source) {
            if (item == null) {
                throw new ValidationException("批量写入项不能为 NULL");
            }
            if (index >= targetIds.size() || !targetIds.get(index).equals(item.getTargetId())) {
                throw new PermissionDeniedException(
                    "批量写入项必须保持授权目标顺序: " + item.getTargetId()
                );
            }
            if (item.getId() == null || !item.getTargetId().equals(String.valueOf(item.getId()))) {
                throw new PermissionDeniedException(
                    "批量写入主键与授权目标不一致: " + item.getTargetId()
                );
            }
            if (item.getOperation() == EntityDaoBatchWriteOperation.UPDATE
                && (item.getPatch() == null || item.getPatch().getId() == null
                    || !String.valueOf(item.getId()).equals(String.valueOf(item.getPatch().getId())))) {
                throw new PermissionDeniedException(
                    "批量 UPDATE Patch 主键与授权目标不一致: " + item.getTargetId()
                );
            }
            if (operation == null) {
                operation = item.getOperation();
            } else if (operation != item.getOperation()) {
                throw new ValidationException("批量写入不能混合 UPDATE 和 DELETE");
            }
            if (!item.getOperation().name().toLowerCase(java.util.Locale.ROOT)
                .equals(authorization.getOperation().getOperationCode())) {
                throw new PermissionDeniedException("批量写入形状与授权操作不一致");
            }
            copy.add(item);
            index++;
        }
        this.items = Collections.unmodifiableList(copy);
    }

    /** 创建按授权目标顺序绑定的全量原子批量写入请求。 */
    public static <T, ID> EntityDaoBatchWriteRequest<T, ID> of(
        EntityDaoBatchAuthorizationContext authorization,
        Collection<? extends Item<T, ID>> items
    ) {
        return new EntityDaoBatchWriteRequest<T, ID>(authorization, items);
    }

    /** 返回本请求绑定的授权上下文。 */
    public EntityDaoBatchAuthorizationContext getAuthorization() {
        return authorization;
    }

    /** 禁止将同目标的另一批授权结果复用于本次执行。 */
    public void requireApplicable(EntityDaoBatchAuthorizationContext current) {
        if (authorization != current) {
            throw new PermissionDeniedException("批量写入请求不属于本次授权调用");
        }
    }

    /** 返回按授权目标顺序保存的不可变写入项。 */
    public List<Item<T, ID>> getItems() {
        return items;
    }

    /** 当前合同只允许全量原子执行。 */
    public EntityDaoBatchAtomicity getAtomicity() {
        return EntityDaoBatchAtomicity.ALL_OR_NOTHING;
    }

    /** 单个授权目标的批量写入项。 */
    public static final class Item<T, ID> {
        private final String targetId;
        private final ID id;
        private final EntityDaoBatchWriteOperation operation;
        private final UpdatePatch<T> patch;
        private final Long expectedVersion;

        private Item(
            String targetId,
            ID id,
            EntityDaoBatchWriteOperation operation,
            UpdatePatch<T> patch,
            Long expectedVersion
        ) {
            if (targetId == null || targetId.trim().isEmpty() || !targetId.equals(targetId.trim())) {
                throw new ValidationException("批量写入目标 ID 不能为空或包含首尾空格");
            }
            this.targetId = targetId;
            this.id = Objects.requireNonNull(id, "批量写入主键不能为空");
            this.operation = Objects.requireNonNull(operation, "批量写入操作不能为空");
            if (operation == EntityDaoBatchWriteOperation.UPDATE && patch == null) {
                throw new ValidationException("批量 UPDATE 必须携带 UpdatePatch");
            }
            if (operation == EntityDaoBatchWriteOperation.DELETE && patch != null) {
                throw new ValidationException("批量 DELETE 不能携带 UpdatePatch");
            }
            if (expectedVersion != null && expectedVersion.longValue() < 0L) {
                throw new ValidationException("批量写入预期版本不能小于 0");
            }
            this.patch = patch;
            this.expectedVersion = expectedVersion;
        }

        /** 创建局部更新项；预期版本由 Patch 提供。 */
        public static <T, ID> Item<T, ID> update(
            String targetId,
            ID id,
            UpdatePatch<T> patch
        ) {
            Long expectedVersion = patch == null ? null : patch.getExpectedVersion();
            return new Item<T, ID>(
                targetId, id, EntityDaoBatchWriteOperation.UPDATE, patch, expectedVersion
            );
        }

        /** 创建不带预期版本的删除项。 */
        public static <T, ID> Item<T, ID> delete(String targetId, ID id) {
            return delete(targetId, id, null);
        }

        /** 创建带可选预期版本的删除项。 */
        public static <T, ID> Item<T, ID> delete(String targetId, ID id, Long expectedVersion) {
            return new Item<T, ID>(
                targetId, id, EntityDaoBatchWriteOperation.DELETE, null, expectedVersion
            );
        }

        public String getTargetId() {
            return targetId;
        }

        public ID getId() {
            return id;
        }

        public EntityDaoBatchWriteOperation getOperation() {
            return operation;
        }

        public UpdatePatch<T> getPatch() {
            return patch;
        }

        public Long getExpectedVersion() {
            return expectedVersion;
        }
    }
}
