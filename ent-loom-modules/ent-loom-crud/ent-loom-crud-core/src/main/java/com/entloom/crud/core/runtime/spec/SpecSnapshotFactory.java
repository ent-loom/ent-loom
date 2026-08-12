package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * Spec 快照工厂。
 */
public final class SpecSnapshotFactory {
    private SpecSnapshotFactory() {
    }

    public static SubjectContext copySubject(SubjectContext source) {
        return BaseSpec.copySubject(source);
    }

    public static <R> QuerySpec<R> copy(QuerySpec<R> source) {
        return QuerySpec.<R>builder().from(source).build();
    }

    public static <R> QueryExecutionSpec<R> toExecution(QuerySpec<R> source) {
        return QueryExecutionSpec.from(source);
    }

    public static <P> CommandSpec<P> copy(CommandSpec<P> source) {
        return CommandSpec.<P>builder().from(source).build();
    }

    public static <P> CommandExecutionSpec<P> toExecution(CommandSpec<P> source) {
        return CommandExecutionSpec.from(source);
    }
}
