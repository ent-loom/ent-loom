package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Collection;

/**
 * SQL 参数限制器。
 */
public class SqlParameterLimiter {
    /** IN 条件最大数量。 */
    private final int maxInSize;
    /** 字符串最大长度。 */
    private final int maxStringLength;
    /** 最大批量条数。 */
    private final int maxBatchSize;

    public SqlParameterLimiter(int maxInSize, int maxStringLength, int maxBatchSize) {
        this.maxInSize = maxInSize;
        this.maxStringLength = maxStringLength;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * 使用默认阈值创建。
     */
    public SqlParameterLimiter() {
        this(1000, 2048, 200);
    }

    /**
     * 校验查询参数规模。
     *
     * @param spec 查询 spec
     */
    /**
     * 校验查询规格。
     */
    public void validateQuerySpec(QuerySpec<?> spec) {
        validateFilterableSpec(spec);
    }

    /**
     * 校验带过滤视图的参数规模。
     */
    public void validateFilterableSpec(FilterableSpec spec) {
        for (QueryFilter filter : spec.getFilters()) {
            Object value = filter.getValue();
            if (value instanceof String) {
                String str = (String) value;
                if (str.length() > maxStringLength) {
                    throw new ValidationException("字符串参数过长: " + filter.getField());
                }
            }
            if ((filter.getOperator() == FilterOperator.IN || filter.getOperator() == FilterOperator.NOT_IN)
                && value instanceof Collection<?>) {
                Collection<?> collection = (Collection<?>) value;
                if (collection.size() > maxInSize) {
                    throw new ValidationException("IN 列表过大: " + filter.getField());
                }
            }
        }
    }

    /**
     * 校验命令参数规模。
     *
     * @param spec 命令 spec
     */
    /**
     * 校验命令规格。
     */
    public void validateCommandSpec(CommandSpec<?> spec) {
        Object payload = spec.getPayload();
        if (payload instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) payload;
            if (collection.size() > maxBatchSize) {
                throw new ValidationException("批量载荷过大");
            }
        }
        for (QueryFilter filter : spec.getTargetFilters()) {
            Object value = filter.getValue();
            if (value instanceof String && ((String) value).length() > maxStringLength) {
                throw new ValidationException("目标选择器字符串参数过长: " + filter.getField());
            }
            if ((filter.getOperator() == FilterOperator.IN || filter.getOperator() == FilterOperator.NOT_IN)
                && value instanceof Collection<?>
                && ((Collection<?>) value).size() > maxInSize) {
                throw new ValidationException("目标选择器 IN 列表过大: " + filter.getField());
            }
        }
    }
}
