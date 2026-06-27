package com.entloom.crud.core.runtime.engine;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudScopedOperation;
import com.entloom.crud.core.exception.UnsupportedQueryStrategyException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.enums.QueryStrategy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 默认引擎能力声明。
 *
 * <p>能力声明是默认引擎的前置合同：协议字段只有在能力中明确出现时才允许进入
 * planner/compiler/executor。</p>
 */
public final class EngineCapability {
    /** 引擎名称。 */
    private final String engineName;
    /** 支持的 CRUD 操作。 */
    private final Set<CrudOperationKey> operations;
    /** 支持的查询策略。 */
    private final Set<QueryStrategy> queryStrategies;
    /** 支持的细粒度特性。 */
    private final Set<EngineFeature> features;

    private EngineCapability(Builder builder) {
        this.engineName = normalizeName(builder.engineName);
        this.operations = Collections.unmodifiableSet(new LinkedHashSet<CrudOperationKey>(builder.operations));
        this.queryStrategies = immutableCopy(builder.queryStrategies, QueryStrategy.class);
        this.features = immutableCopy(builder.features, EngineFeature.class);
    }

    public static Builder builder(String engineName) {
        return new Builder(engineName);
    }

    public static EngineCapability unknown(String engineName) {
        return builder(engineName)
            .queryStrategies(EnumSet.allOf(QueryStrategy.class))
            .features(EnumSet.allOf(EngineFeature.class))
            .build();
    }

    public String getEngineName() {
        return engineName;
    }

    public Set<CrudOperationKey> getOperations() {
        return operations;
    }

    public Set<QueryStrategy> getQueryStrategies() {
        return queryStrategies;
    }

    public Set<EngineFeature> getFeatures() {
        return features;
    }

    public boolean supportsOperation(CrudOperationKey operationKey) {
        return operationKey != null && operations.contains(operationKey);
    }

    public boolean supportsQueryStrategy(QueryStrategy strategy) {
        return strategy != null && queryStrategies.contains(strategy);
    }

    public boolean supportsFeature(EngineFeature feature) {
        return feature != null && features.contains(feature);
    }

    public void requireOperation(CrudOperationKey operationKey) {
        if (!supportsOperation(operationKey)) {
            throw new ValidationException(engineName + " 不支持操作: " + operationKey);
        }
    }

    public void requireQueryStrategy(QueryStrategy strategy) {
        if (!supportsQueryStrategy(strategy)) {
            throw new UnsupportedQueryStrategyException(engineName + " 不支持查询策略: " + strategy);
        }
    }

    public void requireFeature(EngineFeature feature, String reason) {
        if (!supportsFeature(feature)) {
            throw new ValidationException(engineName + " 不支持 " + reason);
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "default-engine";
        }
        return name.trim();
    }

    private static <E extends Enum<E>> Set<E> immutableCopy(Set<E> source, Class<E> enumType) {
        if (source == null || source.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(enumType));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }

    public static final class Builder {
        /** 引擎名称。 */
        private final String engineName;
        /** 支持的 CRUD 操作。 */
        private Set<CrudOperationKey> operations = new LinkedHashSet<CrudOperationKey>();
        /** 支持的查询策略。 */
        private Set<QueryStrategy> queryStrategies = EnumSet.noneOf(QueryStrategy.class);
        /** 支持的细粒度特性。 */
        private Set<EngineFeature> features = EnumSet.noneOf(EngineFeature.class);

        private Builder(String engineName) {
            this.engineName = engineName;
        }

        public Builder operations(Set<CrudOperationKey> operations) {
            this.operations = operations == null ? new LinkedHashSet<CrudOperationKey>() : new LinkedHashSet<CrudOperationKey>(operations);
            return this;
        }

        public Builder operations(CrudScopedOperation first, CrudScopedOperation... rest) {
            Objects.requireNonNull(first, "first 不能为空");
            Set<CrudOperationKey> target = new LinkedHashSet<CrudOperationKey>(this.operations);
            target.add(CrudOperationKey.of(first));
            if (rest != null) {
                for (CrudScopedOperation item : rest) {
                    if (item != null) {
                        target.add(CrudOperationKey.of(item));
                    }
                }
            }
            this.operations = target;
            return this;
        }

        public Builder queryStrategies(Set<QueryStrategy> queryStrategies) {
            this.queryStrategies = copy(queryStrategies, QueryStrategy.class);
            return this;
        }

        public Builder queryStrategies(QueryStrategy first, QueryStrategy... rest) {
            this.queryStrategies = copy(first, rest, QueryStrategy.class);
            return this;
        }

        public Builder features(Set<EngineFeature> features) {
            this.features = copy(features, EngineFeature.class);
            return this;
        }

        public Builder features(EngineFeature first, EngineFeature... rest) {
            this.features = copy(first, rest, EngineFeature.class);
            return this;
        }

        public EngineCapability build() {
            return new EngineCapability(this);
        }

        private static <E extends Enum<E>> Set<E> copy(Set<E> source, Class<E> enumType) {
            if (source == null || source.isEmpty()) {
                return EnumSet.noneOf(enumType);
            }
            return EnumSet.copyOf(source);
        }

        private static <E extends Enum<E>> Set<E> copy(E first, E[] rest, Class<E> enumType) {
            Objects.requireNonNull(first, "first 不能为空");
            Set<E> target = EnumSet.noneOf(enumType);
            target.add(first);
            if (rest != null) {
                for (E item : rest) {
                    if (item != null) {
                        target.add(item);
                    }
                }
            }
            return target;
        }
    }
}
