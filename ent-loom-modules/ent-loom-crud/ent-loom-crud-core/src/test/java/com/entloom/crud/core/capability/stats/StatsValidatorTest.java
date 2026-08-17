package com.entloom.crud.core.capability.stats;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatsValidatorTest {
    @Test
    void filterAndGroupByRequireFilterableFields() {
        StatsQueryPayload filterPayload = countPayload();
        StatsSpec filterSpec = StatsSpec.builder()
            .filters(Collections.singletonList(new QueryFilter("sortOnly", FilterOperator.EQ, "x")))
            .payload(filterPayload)
            .build();

        assertThrows(ValidationException.class, () -> new StatsValidator().validate(filterSpec, meta(), filterPayload));

        StatsQueryPayload groupPayload = countPayload();
        groupPayload.setGroupBy(Collections.singletonList(new StatsGroupBy("sortOnly")));
        StatsSpec groupSpec = StatsSpec.builder().payload(groupPayload).build();

        assertThrows(ValidationException.class, () -> new StatsValidator().validate(groupSpec, meta(), groupPayload));
    }

    @Test
    void dimensionSortRequiresSortableField() {
        StatsQueryPayload payload = countPayload();
        StatsSpec spec = StatsSpec.builder()
            .sorts(Collections.singletonList(new QuerySort("filterOnly", SortDirection.ASC, SortTarget.DIMENSION)))
            .payload(payload)
            .build();

        assertThrows(ValidationException.class, () -> new StatsValidator().validate(spec, meta(), payload));
    }

    @Test
    void dimensionAliasSortCannotBypassSortableField() {
        StatsQueryPayload payload = countPayload();
        StatsGroupBy groupBy = new StatsGroupBy("filterOnly");
        groupBy.setAlias("x");
        payload.setGroupBy(Collections.singletonList(groupBy));
        StatsSpec spec = StatsSpec.builder()
            .sorts(Collections.singletonList(new QuerySort(" X ", SortDirection.ASC, SortTarget.DIMENSION)))
            .payload(payload)
            .build();

        assertThrows(ValidationException.class, () -> new StatsValidator().validate(spec, meta(), payload));
    }

    @Test
    void fieldSortRequiresSortableGroupByField() {
        StatsQueryPayload payload = countPayload();
        payload.setGroupBy(Collections.singletonList(new StatsGroupBy("filterOnly")));
        StatsSpec spec = StatsSpec.builder()
            .sorts(Collections.singletonList(new QuerySort("filterOnly", SortDirection.ASC, SortTarget.FIELD)))
            .payload(payload)
            .build();

        assertThrows(ValidationException.class, () -> new StatsValidator().validate(spec, meta(), payload));
    }

    @Test
    void sortableDimensionSortPassesForAliasAndFieldTargets() {
        StatsQueryPayload payload = countPayload();
        StatsGroupBy groupBy = new StatsGroupBy("both");
        groupBy.setAlias("dimensionAlias");
        payload.setGroupBy(Collections.singletonList(groupBy));

        StatsSpec aliasSpec = StatsSpec.builder()
            .sorts(Collections.singletonList(new QuerySort("dimensionAlias", SortDirection.ASC, SortTarget.DIMENSION)))
            .payload(payload)
            .build();
        StatsSpec fieldSpec = StatsSpec.builder()
            .sorts(Collections.singletonList(new QuerySort("both", SortDirection.ASC, SortTarget.FIELD)))
            .payload(payload)
            .build();

        StatsValidator validator = new StatsValidator();
        validator.validate(aliasSpec, meta(), payload);
        validator.validate(fieldSpec, meta(), payload);
    }

    private static StatsQueryPayload countPayload() {
        StatsQueryPayload payload = new StatsQueryPayload();
        payload.setMetrics(Collections.singletonList(new StatsMetric("COUNT", null, "total")));
        return payload;
    }

    private static EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("filterOnly", new EntityFieldMeta("filterOnly", String.class, "filter_only", true, false, true, false));
        fields.put("sortOnly", new EntityFieldMeta("sortOnly", String.class, "sort_only", true, false, false, true));
        fields.put("both", new EntityFieldMeta("both", String.class, "both_value", true, false, true, true));
        return new EntityMeta(
            Object.class,
            new ResourceDescriptor(Object.class, "Object", "test-service", Arrays.asList("test")),
            "t_object",
            "id",
            null,
            fields
        );
    }
}
