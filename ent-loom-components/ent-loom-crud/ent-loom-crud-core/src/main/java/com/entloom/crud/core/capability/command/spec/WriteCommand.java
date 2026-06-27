package com.entloom.crud.core.capability.command.spec;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.QueryFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 结构化写入命令。
 *
 * @param <P> 写入字段值类型
 */
public final class WriteCommand<P> {
    /** 子命令操作。 */
    private final CommandOperation op;
    /** 主键值，仅用于定位记录。 */
    private final Object id;
    /** 写入字段值，不包含主键定位语义。 */
    private final P values;
    /** 子命令目标选择器。 */
    private final List<QueryFilter> targetFilters;
    /** 子命令期望版本。 */
    private final Long expectedVersion;

    public WriteCommand(CommandOperation op, P values) {
        this(op, null, values, Collections.<QueryFilter>emptyList(), null);
    }

    public WriteCommand(CommandOperation op, Object id, P values) {
        this(op, id, values, Collections.<QueryFilter>emptyList(), null);
    }

    public WriteCommand(
        CommandOperation op,
        P values,
        List<QueryFilter> targetFilters,
        Long expectedVersion
    ) {
        this(op, null, values, targetFilters, expectedVersion);
    }

    public WriteCommand(
        CommandOperation op,
        Object id,
        P values,
        List<QueryFilter> targetFilters,
        Long expectedVersion
    ) {
        this.op = op;
        this.id = id;
        this.values = values;
        this.targetFilters = Collections.unmodifiableList(copyFilters(targetFilters));
        this.expectedVersion = expectedVersion;
    }

    public CommandOperation getOp() {
        return op;
    }

    public Object getId() {
        return id;
    }

    public P getValues() {
        return values;
    }

    public P getPayload() {
        return values;
    }

    public List<QueryFilter> getTargetFilters() {
        return copyFilters(targetFilters);
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    private static List<QueryFilter> copyFilters(List<QueryFilter> source) {
        List<QueryFilter> target = new ArrayList<QueryFilter>();
        if (source == null) {
            return target;
        }
        for (QueryFilter filter : source) {
            if (filter == null) {
                target.add(null);
                continue;
            }
            target.add(new QueryFilter(filter.getField(), filter.getOperator(), filter.getValue()));
        }
        return target;
    }
}
