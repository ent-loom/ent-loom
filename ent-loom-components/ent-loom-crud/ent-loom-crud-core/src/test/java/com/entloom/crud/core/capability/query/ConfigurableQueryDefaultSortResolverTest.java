package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigurableQueryDefaultSortResolverTest {
    @Test
    void should_resolve_time_desc_and_id_desc_for_page_without_explicit_sort() {
        ConfigurableQueryDefaultSortResolver resolver = defaultResolver();

        List<QuerySort> sorts = resolver.resolve(
            QuerySpec.builder().op(QueryOperation.PAGE).build(),
            metaWithFields("id", "createTime")
        );

        Assertions.assertEquals(2, sorts.size());
        assertSort(sorts.get(0), "createTime", SortDirection.DESC);
        assertSort(sorts.get(1), "id", SortDirection.DESC);
    }

    @Test
    void should_fallback_to_id_when_time_field_missing() {
        ConfigurableQueryDefaultSortResolver resolver = defaultResolver();

        List<QuerySort> sorts = resolver.resolve(
            QuerySpec.builder().op(QueryOperation.LIST).build(),
            metaWithFields("id", "name")
        );

        Assertions.assertEquals(1, sorts.size());
        assertSort(sorts.get(0), "id", SortDirection.DESC);
    }

    @Test
    void should_not_resolve_when_explicit_sort_exists() {
        ConfigurableQueryDefaultSortResolver resolver = defaultResolver();
        QuerySpec<?> spec = QuerySpec.builder()
            .op(QueryOperation.PAGE)
            .sorts(Collections.singletonList(new QuerySort("name", SortDirection.ASC)))
            .build();

        List<QuerySort> sorts = resolver.resolve(spec, metaWithFields("id", "createTime", "name"));

        Assertions.assertTrue(sorts.isEmpty());
    }

    @Test
    void should_honor_disabled_append_id_and_fallback_switches() {
        ConfigurableQueryDefaultSortResolver disabled = newResolver(
            false,
            true,
            true,
            EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST)
        );
        Assertions.assertTrue(
            disabled.resolve(QuerySpec.builder().op(QueryOperation.PAGE).build(), metaWithFields("id", "createTime")).isEmpty()
        );

        ConfigurableQueryDefaultSortResolver noAppendId = newResolver(
            true,
            false,
            true,
            EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST)
        );
        List<QuerySort> timeOnly = noAppendId.resolve(
            QuerySpec.builder().op(QueryOperation.PAGE).build(),
            metaWithFields("id", "createTime")
        );
        Assertions.assertEquals(1, timeOnly.size());
        assertSort(timeOnly.get(0), "createTime", SortDirection.DESC);

        ConfigurableQueryDefaultSortResolver noFallback = newResolver(
            true,
            true,
            false,
            EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST)
        );
        Assertions.assertTrue(
            noFallback.resolve(QuerySpec.builder().op(QueryOperation.PAGE).build(), metaWithFields("id", "name")).isEmpty()
        );
    }

    @Test
    void should_not_apply_to_operations_outside_apply_to() {
        ConfigurableQueryDefaultSortResolver resolver = defaultResolver();

        List<QuerySort> sorts = resolver.resolve(
            QuerySpec.builder().op(QueryOperation.FIND_ONE).build(),
            metaWithFields("id", "createTime")
        );

        Assertions.assertTrue(sorts.isEmpty());
    }

    private static ConfigurableQueryDefaultSortResolver defaultResolver() {
        return newResolver(true, true, true, EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST));
    }

    private static ConfigurableQueryDefaultSortResolver newResolver(
        boolean enabled,
        boolean appendId,
        boolean fallbackToId,
        EnumSet<QueryOperation> applyTo
    ) {
        return new ConfigurableQueryDefaultSortResolver(
            enabled,
            applyTo,
            Arrays.asList("createTime", "createdAt"),
            SortDirection.DESC,
            appendId,
            SortDirection.DESC,
            fallbackToId
        );
    }

    private static EntityMeta metaWithFields(String... fields) {
        Map<String, EntityFieldMeta> metas = new LinkedHashMap<String, EntityFieldMeta>();
        for (String field : fields) {
            metas.put(field, new EntityFieldMeta(field, String.class, columnName(field), true, false, true, true));
        }
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "TestEntity", "test-service", Collections.<String>emptyList()),
            "t_test",
            "id",
            null,
            metas
        );
    }

    private static String columnName(String field) {
        if ("createTime".equals(field)) {
            return "create_time";
        }
        return field;
    }

    private static void assertSort(QuerySort sort, String field, SortDirection direction) {
        Assertions.assertEquals(field, sort.getField());
        Assertions.assertEquals(direction, sort.getDirection());
        Assertions.assertEquals(SortTarget.FIELD, sort.getTarget());
    }

    private static class TestEntity {
    }
}
