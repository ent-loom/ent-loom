package com.entloom.crud.core.foundation.read.relation;

/**
 * 关系查询治理策略。
 */
public final class RelationQueryPolicy {
    private final int maxDepth;
    private final int maxExpandEdges;
    private final boolean allowCycles;
    private final boolean allowExternalLoaders;

    public RelationQueryPolicy() {
        this(1, 32, false, false);
    }

    public RelationQueryPolicy(
        int maxDepth,
        int maxExpandEdges,
        boolean allowCycles,
        boolean allowExternalLoaders
    ) {
        this.maxDepth = positive(maxDepth, 1);
        this.maxExpandEdges = positive(maxExpandEdges, 32);
        this.allowCycles = allowCycles;
        this.allowExternalLoaders = allowExternalLoaders;
    }

    public static RelationQueryPolicy defaultPolicy() {
        return new RelationQueryPolicy();
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getMaxExpandEdges() {
        return maxExpandEdges;
    }

    public boolean isAllowCycles() {
        return allowCycles;
    }

    public boolean isAllowExternalLoaders() {
        return allowExternalLoaders;
    }

    private static int positive(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
