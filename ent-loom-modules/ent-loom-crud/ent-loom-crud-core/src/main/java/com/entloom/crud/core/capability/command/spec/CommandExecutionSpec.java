package com.entloom.crud.core.capability.command.spec;

/**
 * 命令执行协议对象（执行态）。
 *
 * @param <P> 入参载荷类型
 */
public final class CommandExecutionSpec<P> extends CommandSpec<P> {
    private CommandExecutionSpec(Builder<P> builder) {
        super(builder);
    }

    public static <P> Builder<P> executionBuilder() {
        return new Builder<P>();
    }

    public static <P> CommandExecutionSpec<P> from(CommandSpec<P> source) {
        return CommandExecutionSpec.<P>executionBuilder().from(source).build();
    }

    public static final class Builder<P> extends CommandSpec.AbstractBuilder<P, Builder<P>> {
        @Override
        protected Builder<P> self() {
            return this;
        }

        @Override
        public CommandExecutionSpec<P> build() {
            return new CommandExecutionSpec<P>(this);
        }
    }
}
