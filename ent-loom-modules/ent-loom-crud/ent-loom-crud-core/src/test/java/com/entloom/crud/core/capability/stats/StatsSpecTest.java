package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.StatsOperation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatsSpecTest {
    @Test
    void should_preserve_stats_preview_operation_key() {
        StatsSpec spec = StatsSpec.builder()
            .operation(StatsOperation.PREVIEW)
            .build();

        Assertions.assertEquals(StatsOperation.PREVIEW, spec.getOperation());
        Assertions.assertEquals(CrudOperationDomain.STATS, spec.getOperationKey().getDomain());
        Assertions.assertEquals("PREVIEW", spec.getOperationKey().getOperation());
        Assertions.assertEquals(StatsOperation.PREVIEW, spec.toBuilder().build().getOperation());
    }
}
