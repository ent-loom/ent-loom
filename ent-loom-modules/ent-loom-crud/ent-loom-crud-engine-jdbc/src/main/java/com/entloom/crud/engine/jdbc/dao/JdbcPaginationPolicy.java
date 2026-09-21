package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.exception.ValidationException;

/**
 * JDBC 自定义查询分页策略。
 *
 * <p>策略集中保存分页保护边界，避免执行器和 Spring 配置各自维护一份硬编码。</p>
 */
public final class JdbcPaginationPolicy {
    /** 默认单页最大条数。 */
    public static final int DEFAULT_MAX_PAGE_SIZE = 200;
    /** 默认最大分页偏移。 */
    public static final long DEFAULT_MAX_OFFSET = 1_000_000L;

    private final int maxPageSize;
    private final long maxOffset;

    /** 创建默认分页策略。 */
    public JdbcPaginationPolicy() {
        this(DEFAULT_MAX_PAGE_SIZE, DEFAULT_MAX_OFFSET);
    }

    /**
     * 创建分页策略。
     *
     * @param maxPageSize 单页最大条数，必须大于 0 且小于 {@link Integer#MAX_VALUE}
     * @param maxOffset 最大偏移，必须大于等于 0
     */
    public JdbcPaginationPolicy(int maxPageSize, long maxOffset) {
        if (maxPageSize <= 0 || maxPageSize == Integer.MAX_VALUE) {
            throw new ValidationException("分页页大小上限必须大于 0 且允许多取一条: " + maxPageSize);
        }
        if (maxOffset < 0) {
            throw new ValidationException("分页偏移上限不能小于 0: " + maxOffset);
        }
        this.maxPageSize = maxPageSize;
        this.maxOffset = maxOffset;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public long getMaxOffset() {
        return maxOffset;
    }
}
