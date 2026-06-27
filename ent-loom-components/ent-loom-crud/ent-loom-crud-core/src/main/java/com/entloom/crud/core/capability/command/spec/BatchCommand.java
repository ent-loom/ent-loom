package com.entloom.crud.core.capability.command.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 结构化批量写入命令。
 *
 * @param <P> 子命令载荷类型
 */
public final class BatchCommand<P> {
    /** 子命令列表。 */
    private final List<WriteCommand<P>> items;

    public BatchCommand(List<WriteCommand<P>> items) {
        this.items = Collections.unmodifiableList(copyItems(items));
    }

    public static <P> BatchCommand<P> of(List<WriteCommand<P>> items) {
        return new BatchCommand<P>(items);
    }

    public List<WriteCommand<P>> getItems() {
        return copyItems(items);
    }

    private static <P> List<WriteCommand<P>> copyItems(List<WriteCommand<P>> source) {
        if (source == null) {
            return new ArrayList<WriteCommand<P>>();
        }
        return new ArrayList<WriteCommand<P>>(source);
    }
}
