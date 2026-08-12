package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExportSpecTest {
    @Test
    void should_build_export_operation_key_and_copy_filters() {
        List<QueryFilter> filters = new ArrayList<QueryFilter>();
        filters.add(new QueryFilter("status", FilterOperator.EQ, "A"));
        ExportSpec spec = ExportSpec.builder()
            .rootType(Object.class)
            .operation(ExportOperation.PREVIEW)
            .format("generic-tabular")
            .filters(filters)
            .build();
        filters.clear();

        Assertions.assertEquals(CrudOperationDomain.EXPORT, spec.getOperationKey().getDomain());
        Assertions.assertEquals("PREVIEW", spec.getOperationKey().getOperation());
        Assertions.assertEquals(1, spec.getFilters().size());

        List<QueryFilter> exposed = spec.getFilters();
        exposed.clear();
        Assertions.assertEquals(1, spec.getFilters().size());
    }
}
