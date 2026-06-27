package com.entloom.crud.spring.config;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CrudPropertiesTest {
    @Test
    void default_sort_should_keep_release_contract_defaults() {
        CrudProperties.Query.DefaultSort defaultSort = new CrudProperties.Query.DefaultSort();

        Assertions.assertEquals(EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST), defaultSort.getApplyTo());
        Assertions.assertEquals(SortDirection.DESC, defaultSort.getTimeDirection());
        Assertions.assertEquals(SortDirection.DESC, defaultSort.getIdDirection());
        Assertions.assertTrue(defaultSort.isAppendId());
        Assertions.assertTrue(defaultSort.isFallbackToId());
    }

    @Test
    void setters_should_copy_mutable_collections_and_drop_blank_time_fields() {
        CrudProperties.Query.DefaultSort defaultSort = new CrudProperties.Query.DefaultSort();
        HashSet<QueryOperation> applyTo = new HashSet<QueryOperation>(Arrays.asList(QueryOperation.PAGE));
        defaultSort.setApplyTo(applyTo);
        applyTo.add(QueryOperation.DETAIL);

        defaultSort.setTimeFields(Arrays.asList("createdAt", " ", null, "gmtCreate"));

        Assertions.assertEquals(EnumSet.of(QueryOperation.PAGE), defaultSort.getApplyTo());
        Assertions.assertEquals(Arrays.asList("createdAt", "gmtCreate"), defaultSort.getTimeFields());
    }

    @Test
    void idempotency_required_ops_should_use_explicit_batch_operations_only() {
        CrudProperties.Idempotency idempotency = new CrudProperties.Idempotency();
        idempotency.setRequiredOps(EnumSet.of(CommandOperation.CREATE_BATCH, CommandOperation.UPDATE_BATCH));

        Assertions.assertEquals(
            EnumSet.of(CommandOperation.CREATE_BATCH, CommandOperation.UPDATE_BATCH),
            idempotency.getRequiredOps()
        );
    }

    @Test
    void import_export_module_switches_should_bind_to_typed_properties() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("entloom.crud.import.enabled", "false");
        values.put("entloom.crud.export.enabled", "false");

        CrudProperties properties = new Binder(new MapConfigurationPropertySource(values))
            .bind("entloom.crud", Bindable.of(CrudProperties.class))
            .get();

        Assertions.assertFalse(properties.getImport().isEnabled());
        Assertions.assertFalse(properties.getExport().isEnabled());
    }

}
