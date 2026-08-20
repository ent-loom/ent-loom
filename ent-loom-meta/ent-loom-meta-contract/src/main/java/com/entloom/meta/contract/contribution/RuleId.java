package com.entloom.meta.contract.contribution;

import java.util.Objects;

/**
 * 稳定的 Meta 规则标识。
 */
public final class RuleId implements Comparable<RuleId> {
    private final String value;

    private RuleId(String value) {
        this.value = value;
    }

    public static RuleId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ruleId 不能为空");
        }
        return new RuleId(value.trim());
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(RuleId other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RuleId && Objects.equals(value, ((RuleId) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
