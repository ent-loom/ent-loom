package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcReflectiveMapperTest {
    @Test
    void read_field_should_support_crud_record_keys() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1001L);
        payload.put("order_id", 3002L);
        CrudRecord record = CrudRecord.copyOf(payload);

        Assertions.assertEquals(1001L, mapper.readField(record, "id"));
        Assertions.assertEquals(3002L, mapper.readField(record, "orderId"));
    }

    @Test
    void map_row_should_support_record_projection_by_component_name() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", 1002L);
        row.put("order_no", "ORD-RECORD");

        TestSummaryRecord result = mapper.mapRow(row, TestSummaryRecord.class);

        Assertions.assertEquals(Long.valueOf(1002L), result.id());
        Assertions.assertEquals("ORD-RECORD", result.orderNo());
    }

    @Test
    void map_row_should_support_immutable_constructor_projection_by_parameter_name() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", 1003L);
        row.put("order_no", "ORD-CONSTRUCTOR");

        ImmutableSummary result = mapper.mapRow(row, ImmutableSummary.class);

        Assertions.assertEquals(Long.valueOf(1003L), result.id);
        Assertions.assertEquals("ORD-CONSTRUCTOR", result.orderNo);
    }

    @Test
    void map_row_should_reject_missing_projection_column_and_null_primitive() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("id", 1004L);
        Assertions.assertThrows(IllegalStateException.class, () -> mapper.mapRow(missing, TestSummaryRecord.class));

        Map<String, Object> nullPrimitive = new LinkedHashMap<String, Object>();
        nullPrimitive.put("id", null);
        Assertions.assertThrows(IllegalStateException.class, () -> mapper.mapRow(nullPrimitive, PrimitiveSummaryRecord.class));
    }

    @Test
    void assign_children_should_return_new_crud_record_for_relation_field() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        CrudRecord root = CrudRecord.copyOf(Collections.<String, Object>singletonMap("id", 1L));
        RelationEdge edge = new RelationEdge();
        edge.setToEntity(TestChild.class);
        edge.setRelationField("items");
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);
        List<Object> children = Collections.<Object>singletonList(new TestChild(9L));

        Object updated = mapper.assignChildren(root, edge, children);

        Assertions.assertTrue(updated instanceof CrudRecord);
        Assertions.assertNotSame(root, updated);
        CrudRecord updatedRecord = (CrudRecord) updated;
        Assertions.assertTrue(updatedRecord.get("items") instanceof List<?>);
        Assertions.assertEquals(1, ((List<?>) updatedRecord.get("items")).size());
    }

    @Test
    void assign_children_should_not_infer_object_relation_field_by_default() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();
        TestRoot root = new TestRoot();
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(TestRoot.class);
        edge.setToEntity(TestChild.class);
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);

        Object updated = mapper.assignChildren(root, edge, Collections.<Object>singletonList(new TestChild(9L)));

        Assertions.assertSame(root, updated);
        Assertions.assertNull(root.children);
    }

    @Test
    void assign_children_should_allow_explicit_relation_field_fallback_when_enabled() {
        JdbcReflectiveMapper mapper = new JdbcReflectiveMapper(true);
        TestRoot root = new TestRoot();
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(TestRoot.class);
        edge.setToEntity(TestChild.class);
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);

        mapper.assignChildren(root, edge, Collections.<Object>singletonList(new TestChild(9L)));

        Assertions.assertNotNull(root.children);
        Assertions.assertEquals(1, root.children.size());
    }

    private static final class TestRoot {
        private List<TestChild> children;
    }

    private static final class TestChild {
        private final Long id;

        private TestChild(Long id) {
            this.id = id;
        }
    }

    private record TestSummaryRecord(Long id, String orderNo) {
    }

    private static final class ImmutableSummary {
        private final Long id;
        private final String orderNo;

        private ImmutableSummary(Long id, String orderNo) {
            this.id = id;
            this.orderNo = orderNo;
        }
    }

    private record PrimitiveSummaryRecord(long id) {
    }
}
