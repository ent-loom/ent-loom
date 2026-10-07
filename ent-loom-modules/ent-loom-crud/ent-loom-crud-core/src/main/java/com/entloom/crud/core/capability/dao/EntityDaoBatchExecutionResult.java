package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 逐目标批量执行结果的 Core 形状合同。
 *
 * <p>结果必须与授权批次逐项、按顺序对应；本类型只描述每个目标的观测结果，不表示事务已提交、
 * 已回滚或允许部分成功。授权拒绝不会进入结果集合，而是在批次授权阶段整体失败。</p>
 */
public final class EntityDaoBatchExecutionResult {
    private final List<Item> items;
    private final Map<String, Item> itemsByTarget;
    private final EntityDaoBatchAtomicity atomicity;

    private EntityDaoBatchExecutionResult(
        EntityDaoBatchAuthorizationContext authorization,
        Collection<? extends Item> source,
        EntityDaoBatchAtomicity atomicity
    ) {
        Objects.requireNonNull(authorization, "批量授权上下文不能为空");
        Objects.requireNonNull(source, "批量执行结果不能为空");
        this.atomicity = Objects.requireNonNull(atomicity, "批量原子性声明不能为空");
        if (source.size() != authorization.size()) {
            throw new ValidationException("批量执行结果必须逐项对应授权目标");
        }
        List<Item> copy = new ArrayList<Item>();
        Map<String, Item> byTarget = new LinkedHashMap<String, Item>();
        List<String> targetIds = authorization.getTargetIds();
        int index = 0;
        for (Item item : source) {
            if (item == null) {
                throw new ValidationException("批量执行结果不能包含空项");
            }
            if (index >= targetIds.size() || !targetIds.get(index).equals(item.getTargetId())) {
                throw new ValidationException("批量执行结果必须保持授权目标顺序: " + item.getTargetId());
            }
            if (byTarget.put(item.getTargetId(), item) != null) {
                throw new ValidationException("批量执行结果目标不能重复: " + item.getTargetId());
            }
            copy.add(item);
            index++;
        }
        this.items = Collections.unmodifiableList(copy);
        this.itemsByTarget = Collections.unmodifiableMap(byTarget);
        if (this.atomicity == EntityDaoBatchAtomicity.ALL_OR_NOTHING
            && hasSuccess(copy)
            && hasFailure(copy)) {
            throw new ValidationException(
                "全量原子批量结果不能同时包含成功目标和失败目标"
            );
        }
    }

    /** 创建与授权批次逐项目标对应的结果集合。 */
    public static EntityDaoBatchExecutionResult of(
        EntityDaoBatchAuthorizationContext authorization,
        Collection<? extends Item> items
    ) {
        return of(authorization, items, EntityDaoBatchAtomicity.ALL_OR_NOTHING);
    }

    /** 创建带显式原子性声明的逐目标结果集合。 */
    public static EntityDaoBatchExecutionResult of(
        EntityDaoBatchAuthorizationContext authorization,
        Collection<? extends Item> items,
        EntityDaoBatchAtomicity atomicity
    ) {
        return new EntityDaoBatchExecutionResult(authorization, items, atomicity);
    }

    /** 返回按授权目标顺序保存的不可变结果。 */
    public List<Item> getItems() {
        return items;
    }

    /** 返回按目标 ID 索引的不可变结果。 */
    public Map<String, Item> getItemsByTarget() {
        return itemsByTarget;
    }

    /** 返回本结果集合的原子性声明；不表示底层事务已经存在。 */
    public EntityDaoBatchAtomicity getAtomicity() {
        return atomicity;
    }

    /** 是否存在版本冲突或存储执行失败；无数据不是执行失败。 */
    public boolean hasFailure() {
        return hasFailure(items);
    }

    private static boolean hasFailure(Collection<? extends Item> items) {
        for (Item item : items) {
            if (item.getStatus() == Status.VERSION_CONFLICT
                || item.getStatus() == Status.EXECUTION_FAILED) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSuccess(Collection<? extends Item> items) {
        for (Item item : items) {
            if (item.getStatus() == Status.SUCCEEDED) {
                return true;
            }
        }
        return false;
    }

    /** 单个授权目标的结果。 */
    public static final class Item {
        private final String targetId;
        private final Status status;

        private Item(String targetId, Status status) {
            if (targetId == null || targetId.trim().isEmpty() || !targetId.equals(targetId.trim())) {
                throw new ValidationException("批量执行结果目标 ID 不能为空或包含首尾空格");
            }
            this.targetId = targetId;
            this.status = Objects.requireNonNull(status, "批量执行结果状态不能为空");
        }

        public static Item succeeded(String targetId) {
            return new Item(targetId, Status.SUCCEEDED);
        }

        public static Item noData(String targetId) {
            return new Item(targetId, Status.NO_DATA);
        }

        public static Item versionConflict(String targetId) {
            return new Item(targetId, Status.VERSION_CONFLICT);
        }

        public static Item executionFailed(String targetId) {
            return new Item(targetId, Status.EXECUTION_FAILED);
        }

        public String getTargetId() {
            return targetId;
        }

        public Status getStatus() {
            return status;
        }
    }

    /** 逐目标执行观测状态。 */
    public enum Status {
        /** 目标操作已完成。 */
        SUCCEEDED,
        /** 目标已获授权但没有可返回或可影响的数据。 */
        NO_DATA,
        /** 目标的预期版本不匹配。 */
        VERSION_CONFLICT,
        /** 存储或映射执行失败。 */
        EXECUTION_FAILED
    }
}
