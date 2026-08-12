package com.entloom.crud.core.capability.command.spec;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命令请求协议对象（不可变）。
 *
 * @param <P> 入参载荷类型
 */
public class CommandSpec<P> extends BaseSpec implements OperationKeySpec {
    /** 操作类型。 */
    private final CommandOperation op;
    /** 业务载荷。 */
    private final P payload;
    /** 幂等键。 */
    private final String idempotencyKey;
    /** 期望版本号。 */
    private final Long expectedVersion;
    /** 是否为试运行。 */
    private final boolean dryRun;
    /** 目标过滤条件列表。 */
    private final List<QueryFilter> targetFilters;
    /** 结果类型。 */
    private final Class<?> resultType;

    protected <B extends AbstractBuilder<P, B>> CommandSpec(AbstractBuilder<P, B> builder) {
        super(builder);
        this.op = builder.op;
        this.payload = builder.payload;
        this.idempotencyKey = builder.idempotencyKey;
        this.expectedVersion = builder.expectedVersion;
        this.dryRun = builder.dryRun;
        this.targetFilters = Collections.unmodifiableList(copyFilters(builder.targetFilters));
        this.resultType = builder.resultType;
    }

    public static <P> Builder<P> builder() {
        return new Builder<P>();
    }

    public Builder<P> toBuilder() {
        return new Builder<P>().from(this);
    }

    protected final <B extends AbstractBuilder<P, B>> B copyCommandTo(B builder) {
        return copyBaseTo(builder)
            .op(op)
            .payload(payload)
            .idempotencyKey(idempotencyKey)
            .expectedVersion(expectedVersion)
            .dryRun(dryRun)
            .targetFilters(getTargetFilters())
            .resultType(resultType);
    }

    public final CommandOperation getOp() {
        return op;
    }

    public final CrudOperationKey getOperationKey() {
        return CrudOperationKey.of(op);
    }

    public final P getPayload() {
        return payload;
    }

    public final String getIdempotencyKey() {
        return idempotencyKey;
    }

    public final Long getExpectedVersion() {
        return expectedVersion;
    }

    public final boolean isDryRun() {
        return dryRun;
    }

    public List<QueryFilter> getTargetFilters() {
        return copyFilters(targetFilters);
    }

    public final Class<?> getResultType() {
        return resultType;
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

    public static class Builder<P> extends AbstractBuilder<P, Builder<P>> {
        @Override
        protected Builder<P> self() {
            return this;
        }

        @Override
        public CommandSpec<P> build() {
            return new CommandSpec<P>(this);
        }
    }

    protected abstract static class AbstractBuilder<P, B extends AbstractBuilder<P, B>> extends BaseSpec.AbstractBuilder<B> {
        private CommandOperation op;
        private P payload;
        private String idempotencyKey;
        private Long expectedVersion;
        private boolean dryRun;
        private List<QueryFilter> targetFilters = new ArrayList<QueryFilter>();
        private Class<?> resultType;

        public B op(CommandOperation op) {
            this.op = op;
            return self();
        }

        public B payload(P payload) {
            this.payload = payload;
            return self();
        }

        public B idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return self();
        }

        public B expectedVersion(Long expectedVersion) {
            this.expectedVersion = expectedVersion;
            return self();
        }

        public B dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return self();
        }

        public B targetFilters(List<QueryFilter> targetFilters) {
            this.targetFilters = copyFilters(targetFilters);
            return self();
        }

        public B resultType(Class<?> resultType) {
            this.resultType = resultType;
            return self();
        }

        public B from(CommandSpec<P> source) {
            if (source != null) {
                source.copyCommandTo(self());
            }
            return self();
        }

        public abstract CommandSpec<P> build();
    }
}
