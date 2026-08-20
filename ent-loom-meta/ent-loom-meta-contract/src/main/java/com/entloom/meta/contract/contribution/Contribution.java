package com.entloom.meta.contract.contribution;

import com.entloom.meta.contract.value.MetaValueSource;

/**
 * 单个实体/字段属性的候选贡献。
 *
 * @param <T> 属性值类型
 */
public final class Contribution<T> {
    private final String target;
    private final String entity;
    private final String field;
    private final String property;
    private final T value;
    private final MetaValueSource source;
    private final RuleId ruleId;
    private final Priority priority;

    private Contribution(Builder<T> builder) {
        this.target = normalize(builder.target);
        this.entity = normalize(builder.entity);
        this.field = normalize(builder.field);
        this.property = normalize(builder.property);
        this.value = builder.value;
        this.source = builder.source;
        this.ruleId = builder.ruleId;
        this.priority = builder.priority;
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public String target() {
        return target;
    }

    public String entity() {
        return entity;
    }

    public String field() {
        return field;
    }

    public String property() {
        return property;
    }

    public T value() {
        return value;
    }

    public MetaValueSource source() {
        return source;
    }

    public RuleId ruleId() {
        return ruleId;
    }

    public Priority priority() {
        return priority;
    }

    public String targetKey() {
        if (target != null) {
            return target;
        }
        if (entity == null && field == null) {
            return null;
        }
        return String.valueOf(entity) + "." + String.valueOf(field);
    }

    @Override
    public String toString() {
        return "Contribution{" + targetKey() + "." + property + ", ruleId=" + ruleId + '}';
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static final class Builder<T> {
        private String target;
        private String entity;
        private String field;
        private String property;
        private T value;
        private MetaValueSource source;
        private RuleId ruleId;
        private Priority priority;

        public Builder<T> target(String target) {
            this.target = target;
            return this;
        }

        public Builder<T> entity(String entity) {
            this.entity = entity;
            return this;
        }

        public Builder<T> field(String field) {
            this.field = field;
            return this;
        }

        public Builder<T> property(String property) {
            this.property = property;
            return this;
        }

        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        public Builder<T> source(MetaValueSource source) {
            this.source = source;
            return this;
        }

        public Builder<T> ruleId(RuleId ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Builder<T> ruleId(String ruleId) {
            this.ruleId = ruleId == null ? null : RuleId.of(ruleId);
            return this;
        }

        public Builder<T> priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Contribution<T> build() {
            return new Contribution<T>(this);
        }
    }
}
