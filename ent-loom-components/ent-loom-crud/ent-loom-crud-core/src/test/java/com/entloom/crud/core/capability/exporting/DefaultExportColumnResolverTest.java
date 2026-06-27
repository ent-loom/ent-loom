package com.entloom.crud.core.capability.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultExportColumnResolverTest {
    private final DefaultExportColumnResolver resolver = new DefaultExportColumnResolver();

    @Test
    void defaultColumnsHonorExportMetadata() {
        List<ExportColumn> columns = resolver.resolve(defaultSpec(), meta(), RelationGraph.empty());

        assertEquals(Arrays.asList("orderNo", "studentText", "remark"), keys(columns));
        assertEquals("订单号", columns.get(0).getHeader());
        assertEquals("plain", columns.get(0).getFormat());
        assertEquals("学生", columns.get(1).getHeader());
    }

    @Test
    void explicitHiddenFieldIsAllowedButNonExportableAndTechnicalFieldsFail() {
        List<ExportColumn> hidden = resolver.resolve(explicitSpec("hiddenNote"), meta(), RelationGraph.empty());

        assertEquals(Arrays.asList("hiddenNote"), keys(hidden));
        assertEquals("内部备注", hidden.get(0).getHeader());

        assertThrows(CrudException.class, () -> resolver.resolve(explicitSpec("secretNote"), meta(), RelationGraph.empty()));
        assertThrows(CrudException.class, () -> resolver.resolve(explicitSpec("id"), meta(), RelationGraph.empty()));
    }

    @Test
    void explicitFieldRejectsDisplayOwnerAndRelationForeignKey() {
        assertThrows(CrudException.class, () -> resolver.resolve(explicitSpec("studentId"), meta(), RelationGraph.empty()));
        assertThrows(CrudException.class, () -> resolver.resolve(explicitSpec("schoolId"), relationMeta(), relationGraph()));

        List<ExportColumn> display = resolver.resolve(explicitSpec("studentText"), meta(), RelationGraph.empty());

        assertEquals(Arrays.asList("studentText"), keys(display));
    }

    @Test
    void invalidConfiguredDisplayFieldFailsClosed() {
        assertThrows(CrudException.class, () -> resolver.resolve(defaultSpec(), invalidDisplayMeta(), RelationGraph.empty()));
    }

    private ExportSpec defaultSpec() {
        return ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .build();
    }

    private ExportSpec explicitSpec(String field) {
        return ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .fields(Arrays.asList(field))
            .build();
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, null, null, null, null, null, null));
        fields.put("orderNo", field("orderNo", String.class, true, true, "订单号", "plain", null, null));
        fields.put("studentText", field("studentText", String.class, true, true, "学生姓名", null, null, null));
        fields.put("studentId", field("studentId", Long.class, true, true, "学生", null, null, "studentText"));
        fields.put("hiddenNote", field("hiddenNote", String.class, true, false, "内部备注", null, null, null));
        fields.put("secretNote", field("secretNote", String.class, false, true, null, null, null, null));
        fields.put("remark", field("remark", String.class, null, null, "备注", null, null, null));
        fields.put("deleted", field("deleted", Boolean.class, null, null, null, null, null, null));
        return new EntityMeta(
            OrderEntity.class,
            new ResourceDescriptor(OrderEntity.class, "OrderEntity", "test", null),
            "test_order",
            "id",
            "deleted",
            fields
        );
    }

    private EntityMeta relationMeta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, null, null, null, null, null, null));
        fields.put("schoolId", field("schoolId", Long.class, true, true, "学校", null, null, null));
        fields.put("schoolName", field("schoolName", String.class, true, true, "学校名称", null, null, null));
        return new EntityMeta(
            OrderEntity.class,
            new ResourceDescriptor(OrderEntity.class, "OrderEntity", "test", null),
            "test_order",
            "id",
            null,
            fields
        );
    }

    private RelationGraph relationGraph() {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(OrderEntity.class);
        edge.setToEntity(SchoolEntity.class);
        edge.setFromField("schoolId");
        edge.setToField("id");
        return RelationGraph.of(Arrays.asList(edge));
    }

    private EntityMeta invalidDisplayMeta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, null, null, null, null, null, null));
        fields.put("studentId", field("studentId", Long.class, true, true, "学生", null, null, "student.name"));
        fields.put("deleted", field("deleted", Boolean.class, null, null, null, null, null, null));
        return new EntityMeta(
            OrderEntity.class,
            new ResourceDescriptor(OrderEntity.class, "OrderEntity", "test", null),
            "test_order",
            "id",
            "deleted",
            fields
        );
    }

    private EntityFieldMeta field(
        String fieldName,
        Class<?> javaType,
        Boolean exportable,
        Boolean exportDefaultVisible,
        String exportLabel,
        String exportFormat,
        String dictionaryCode,
        String displayField
    ) {
        return new EntityFieldMeta(
            fieldName,
            javaType,
            fieldName,
            true,
            false,
            true,
            true,
            exportable,
            exportDefaultVisible,
            exportLabel,
            exportFormat,
            dictionaryCode,
            displayField
        );
    }

    private List<String> keys(List<ExportColumn> columns) {
        List<String> keys = new ArrayList<String>();
        for (ExportColumn column : columns) {
            keys.add(column.getKey());
        }
        return keys;
    }

    private static final class OrderEntity {
    }

    private static final class SchoolEntity {
    }
}
