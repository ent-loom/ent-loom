package com.entloom.crud.core.util;

import com.entloom.crud.enums.QueryStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class QueryStrategyResolverTest {
    @Test
    void should_prefer_handler_default_when_it_is_explicit() {
        QueryStrategy effective = QueryStrategyResolver.resolveEffectiveStrategy(
            QueryStrategy.ROOT_FIRST,
            QueryStrategy.DEFAULT,
            QueryStrategy.DEFAULT
        );

        Assertions.assertEquals(QueryStrategy.ROOT_FIRST, effective);
    }

    @Test
    void should_use_spec_strategy_when_handler_default_is_default() {
        QueryStrategy effective = QueryStrategyResolver.resolveEffectiveStrategy(
            QueryStrategy.DEFAULT,
            QueryStrategy.ROOT_FIRST,
            QueryStrategy.DEFAULT
        );

        Assertions.assertEquals(QueryStrategy.ROOT_FIRST, effective);
    }

    @Test
    void should_use_engine_default_when_handler_and_spec_are_default() {
        QueryStrategy effective = QueryStrategyResolver.resolveEffectiveStrategy(
            QueryStrategy.DEFAULT,
            QueryStrategy.DEFAULT,
            QueryStrategy.ROOT_FIRST
        );

        Assertions.assertEquals(QueryStrategy.ROOT_FIRST, effective);
    }

    @Test
    void should_fallback_to_root_first_when_every_input_is_null_or_default() {
        Assertions.assertEquals(
            QueryStrategy.ROOT_FIRST,
            QueryStrategyResolver.resolveEffectiveStrategy(null, QueryStrategy.DEFAULT, null)
        );
    }
}
