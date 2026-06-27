package com.entloom.crud.core.runtime.engine;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.exception.UnsupportedQueryStrategyException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.enums.QueryStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EngineCapabilityTest {
    @Test
    void require_should_accept_declared_operations_strategy_and_feature() {
        EngineCapability capability = EngineCapability.builder("test-engine")
            .operations(QueryOperation.LIST, CommandOperation.CREATE)
            .queryStrategies(QueryStrategy.ROOT_FIRST)
            .features(EngineFeature.ROOT_FILTER)
            .build();

        capability.requireOperation(CrudOperationKey.of(QueryOperation.LIST));
        capability.requireOperation(CrudOperationKey.of(CommandOperation.CREATE));
        capability.requireQueryStrategy(QueryStrategy.ROOT_FIRST);
        capability.requireFeature(EngineFeature.ROOT_FILTER, "根过滤");

        Assertions.assertTrue(capability.supportsOperation(CrudOperationKey.of(QueryOperation.LIST)));
        Assertions.assertFalse(capability.supportsOperation(CrudOperationKey.of(StatsOperation.QUERY)));
    }

    @Test
    void require_should_reject_undeclared_capability() {
        EngineCapability capability = EngineCapability.builder("test-engine")
            .operations(QueryOperation.LIST)
            .queryStrategies(QueryStrategy.ROOT_FIRST)
            .features(EngineFeature.ROOT_FILTER)
            .build();

        ValidationException opError = Assertions.assertThrows(
            ValidationException.class,
            () -> capability.requireOperation(CrudOperationKey.of(StatsOperation.QUERY))
        );
        Assertions.assertTrue(opError.getMessage().contains("STATS/QUERY"));

        UnsupportedQueryStrategyException strategyError = Assertions.assertThrows(
            UnsupportedQueryStrategyException.class,
            () -> capability.requireQueryStrategy(QueryStrategy.DEFAULT)
        );
        Assertions.assertTrue(strategyError.getMessage().contains("DEFAULT"));

        ValidationException featureError = Assertions.assertThrows(
            ValidationException.class,
            () -> capability.requireFeature(EngineFeature.SELECT_FIELDS, "显式字段投影")
        );
        Assertions.assertTrue(featureError.getMessage().contains("显式字段投影"));
    }
}
