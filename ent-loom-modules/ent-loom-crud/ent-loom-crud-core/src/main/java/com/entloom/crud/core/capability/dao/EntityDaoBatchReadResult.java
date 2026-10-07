package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 只读批量实体值与逐目标执行观测结果。 */
public final class EntityDaoBatchReadResult<T, ID> {
    private final EntityDaoBatchExecutionResult execution;
    private final Map<ID, T> values;

    private EntityDaoBatchReadResult(
        EntityDaoBatchExecutionResult execution,
        Map<ID, T> values
    ) {
        this.execution = Objects.requireNonNull(execution, "批量执行结果不能为空");
        Map<ID, T> copy = new LinkedHashMap<ID, T>(
            Objects.requireNonNull(values, "批量读取值不能为空")
        );
        Map<String, ID> valuesByTarget = new LinkedHashMap<String, ID>();
        for (Map.Entry<ID, T> entry : copy.entrySet()) {
            ID id = entry.getKey();
            if (id == null || entry.getValue() == null) {
                throw new ValidationException("批量读取值的主键和实体不能为空");
            }
            String targetId = String.valueOf(id);
            EntityDaoBatchExecutionResult.Item item = execution.getItemsByTarget().get(targetId);
            if (item == null) {
                throw new ValidationException("批量读取值必须对应授权目标: " + targetId);
            }
            if (item.getStatus() != EntityDaoBatchExecutionResult.Status.SUCCEEDED) {
                throw new ValidationException("无成功状态的批量目标不能返回实体值: " + targetId);
            }
            if (valuesByTarget.put(targetId, id) != null) {
                throw new ValidationException("批量读取值目标不能重复: " + targetId);
            }
        }
        for (EntityDaoBatchExecutionResult.Item item : execution.getItems()) {
            if (item.getStatus() == EntityDaoBatchExecutionResult.Status.SUCCEEDED
                && !valuesByTarget.containsKey(item.getTargetId())) {
                throw new ValidationException("成功批量目标必须返回实体值: " + item.getTargetId());
            }
        }
        Map<ID, T> ordered = new LinkedHashMap<ID, T>();
        for (EntityDaoBatchExecutionResult.Item item : execution.getItems()) {
            if (item.getStatus() == EntityDaoBatchExecutionResult.Status.SUCCEEDED) {
                ID id = valuesByTarget.get(item.getTargetId());
                ordered.put(id, copy.get(id));
            }
        }
        this.values = Collections.unmodifiableMap(ordered);
    }

    /** 创建不可变的只读批量结果。 */
    public static <T, ID> EntityDaoBatchReadResult<T, ID> of(
        EntityDaoBatchExecutionResult execution,
        Map<ID, T> values
    ) {
        return new EntityDaoBatchReadResult<T, ID>(execution, values);
    }

    /** 返回逐目标执行状态。 */
    public EntityDaoBatchExecutionResult getExecution() {
        return execution;
    }

    /** 返回按授权目标顺序索引的命中实体；无数据和执行失败目标省略。 */
    public Map<ID, T> getValues() {
        return values;
    }
}
