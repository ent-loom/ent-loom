package com.entloom.crud.core.util;

import com.entloom.crud.enums.QueryStrategy;

/**
 * 查询策略解析器。
 */
public final class QueryStrategyResolver {
    private QueryStrategyResolver() {
    }

    /**
     * 解析有效查询策略。
     *
     * @param handlerDefault 处理器默认策略
     * @param specStrategy spec 指定策略
     * @param engineDefault 引擎默认策略
     * @return 有效策略
     */
    public static QueryStrategy resolveEffectiveStrategy(
        QueryStrategy handlerDefault,
        QueryStrategy specStrategy,
        QueryStrategy engineDefault
    ) {
        QueryStrategy strategy = normalize(handlerDefault);
        if (strategy != null) {
            return strategy;
        }
        strategy = normalize(specStrategy);
        if (strategy != null) {
            return strategy;
        }
        strategy = normalize(engineDefault);
        if (strategy != null) {
            return strategy;
        }
        return QueryStrategy.ROOT_FIRST;
    }


    private static QueryStrategy normalize(QueryStrategy strategy) {
        if (strategy == null || strategy == QueryStrategy.DEFAULT) {
            return null;
        }
        return strategy;
    }
}
