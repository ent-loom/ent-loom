package com.entloom.meta.contract.value;

import java.util.Objects;

/**
 * 带来源和值状态的元数据值。
 */
public final class SourcedValue<T> {
    private final T value;
    private final MetaValueSource source;
    private final MetaValueState state;
    private final boolean explicit;
    private final String ruleId;

    private SourcedValue(T value, MetaValueSource source, MetaValueState state, boolean explicit) {
        this(value, source, state, explicit, null);
    }

    private SourcedValue(
        T value,
        MetaValueSource source,
        MetaValueState state,
        boolean explicit,
        String ruleId
    ) {
        this.value = value;
        this.source = source == null ? MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN : source;
        this.state = state == null ? MetaValueState.UNKNOWN : state;
        this.explicit = explicit;
        this.ruleId = trimToNull(ruleId);
    }

    public static <T> SourcedValue<T> explicit(T value, MetaValueSource source) {
        return new SourcedValue<T>(value, source, MetaValueState.EXPLICIT, true);
    }

    public static <T> SourcedValue<T> metaExplicit(T value) {
        return explicit(value, MetaValueSource.META_EXPLICIT);
    }

    public static <T> SourcedValue<T> nativeExplicit(T value) {
        return explicit(value, MetaValueSource.NATIVE_EXPLICIT);
    }

    public static <T> SourcedValue<T> businessExplicitOverride(T value) {
        return explicit(value, MetaValueSource.BUSINESS_EXPLICIT_OVERRIDE);
    }

    public static <T> SourcedValue<T> businessDefaultConfig(T value) {
        return new SourcedValue<T>(value, MetaValueSource.BUSINESS_DEFAULT_CONFIG, MetaValueState.DEFAULTED, false);
    }

    public static <T> SourcedValue<T> inferred(T value) {
        return new SourcedValue<T>(value, MetaValueSource.INFERRED, MetaValueState.INFERRED, false);
    }

    public static <T> SourcedValue<T> defaulted(T value) {
        return new SourcedValue<T>(value, MetaValueSource.DEFAULT, MetaValueState.DEFAULTED, false);
    }

    public static <T> SourcedValue<T> unknown(T value) {
        return new SourcedValue<T>(value, MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, MetaValueState.UNKNOWN, false);
    }

    public static <T> SourcedValue<T> of(T value, MetaValueSource source, MetaValueState state, boolean explicit) {
        return new SourcedValue<T>(value, source, state, explicit);
    }

    public static <T> SourcedValue<T> of(
        T value,
        MetaValueSource source,
        MetaValueState state,
        boolean explicit,
        String ruleId
    ) {
        return new SourcedValue<T>(value, source, state, explicit, ruleId);
    }

    public T value() {
        return value;
    }

    public MetaValueSource source() {
        return source;
    }

    public MetaValueState state() {
        return state;
    }

    public boolean explicit() {
        return explicit;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public String ruleId() {
        return ruleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourcedValue)) {
            return false;
        }
        SourcedValue<?> that = (SourcedValue<?>) other;
        // ruleId 仅用于来源追踪，不改变引入规则来源前的值对象相等语义。
        return explicit == that.explicit
            && Objects.equals(value, that.value)
            && source == that.source
            && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, source, state, Boolean.valueOf(explicit));
    }

    @Override
    public String toString() {
        return "SourcedValue{"
            + "value=" + value
            + ", source=" + source
            + ", state=" + state
            + ", explicit=" + explicit
            + ", ruleId='" + ruleId + '\''
            + '}';
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
