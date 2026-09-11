package com.entloom.doc.core.contract;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.model.DocIndexModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.SourcedValue;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.RelationCardinality;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityDocumentationProjectorTest {

    @Test
    void project_should_sort_and_omit_hidden_and_physical_information() throws Exception {
        DocEntityModel order = entity(
            Order.class,
            "order",
            Arrays.asList(
                field("zeta", String.class, EntFieldKind.TEXT, false),
                field("secret", String.class, EntFieldKind.TEXT, true),
                field("status", Status.class, EntFieldKind.ENUM, false),
                field("customerId", Long.class, EntFieldKind.REF_ID, false),
                field("alpha", String.class, EntFieldKind.TEXT, false)
            ),
            Collections.singletonList(relation("customerId", "customer", "customerId", "id")),
            Arrays.asList(
                index("physical_secret_index", Arrays.asList("secret"), true),
                index("physical_alpha_index", Arrays.asList("alpha"), false)
            ),
            false
        );
        DocEntityModel customer = entity(
            Customer.class,
            "customer",
            Collections.singletonList(field("id", Long.class, EntFieldKind.ID, false)),
            Collections.<DocRelationModel>emptyList(),
            Collections.<DocIndexModel>emptyList(),
            false
        );
        DocEntityModel hidden = entity(
            HiddenEntity.class,
            "hidden",
            Collections.singletonList(field("id", Long.class, EntFieldKind.ID, false)),
            Collections.<DocRelationModel>emptyList(),
            Collections.<DocIndexModel>emptyList(),
            true
        );

        Map<String, Object> document = new EntityDocumentationProjector().project(Arrays.asList(order, hidden, customer));
        Assertions.assertEquals(EntityDocumentationProjector.CONTRACT_VERSION, document.get("contractVersion"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) document.get("entities");
        Assertions.assertEquals(Arrays.asList("customer", "order"), values(entities, "resourceCode"));

        Map<String, Object> orderDocument = findBy(entities, "resourceCode", "order");
        Assertions.assertFalse(orderDocument.containsKey("entityClass"));
        Assertions.assertFalse(orderDocument.containsKey("tableName"));
        Assertions.assertFalse(orderDocument.containsKey("hidden"));
        Assertions.assertFalse(orderDocument.containsKey("visibleFor"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) orderDocument.get("fields");
        Assertions.assertEquals(Arrays.asList("alpha", "customerId", "status", "zeta"), values(fields, "property"));
        Assertions.assertFalse(contains(fields, "property", "secret"));
        for (Map<String, Object> field : fields) {
            Assertions.assertFalse(field.containsKey("column"));
            Assertions.assertFalse(field.containsKey("createDefaultValue"));
            Assertions.assertFalse(field.containsKey("javaType"));
            Assertions.assertFalse(field.containsKey("visibleFor"));
        }

        Map<String, Object> status = findBy(fields, "property", "status");
        Assertions.assertEquals("enum", status.get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enumValues = (List<Map<String, Object>>) status.get("enumValues");
        Assertions.assertEquals(Arrays.asList("ACTIVE", "DISABLED"), values(enumValues, "name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) orderDocument.get("relations");
        Assertions.assertEquals(1, relations.size());
        Assertions.assertEquals("customer", relations.get(0).get("targetResourceCode"));
        Assertions.assertFalse(relations.get(0).containsKey("targetEntity"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indexes = (List<Map<String, Object>>) orderDocument.get("indexes");
        Assertions.assertEquals(1, indexes.size());
        Assertions.assertEquals(Arrays.asList("alpha"), indexes.get(0).get("fields"));
        Assertions.assertFalse(indexes.get(0).containsKey("name"));

        assertSnapshot(document, "snapshots/entity-documentation-v1.json");
    }

    private void assertSnapshot(Map<String, Object> document, String resource) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            Assertions.assertNotNull(input, "缺少 JSON snapshot: " + resource);
            Assertions.assertEquals(
                objectMapper.readTree(input),
                objectMapper.readTree(objectMapper.writeValueAsString(document)),
                "JSON snapshot 不一致: " + resource
            );
        }
    }

    @Test
    void project_should_omit_unresolvable_relations_and_return_empty_arrays() {
        DocEntityModel order = entity(
            Order.class,
            "order",
            Collections.singletonList(field("customerId", Long.class, EntFieldKind.REF_ID, false)),
            Collections.singletonList(relation("customerId", "com.example.Customer", "customerId", "id")),
            Collections.<DocIndexModel>emptyList(),
            false
        );

        Map<String, Object> document = new EntityDocumentationProjector().project(Collections.singletonList(order));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) document.get("entities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) entities.get(0).get("relations");
        Assertions.assertTrue(relations.isEmpty());

        Map<String, Object> empty = new EntityDocumentationProjector().project(null);
        Assertions.assertEquals(Collections.emptyList(), empty.get("entities"));
    }

    @Test
    void project_should_keep_v1_shape_and_preserve_future_field_and_enum_values() {
        DocEntityModel future = entity(
            FutureEntity.class,
            "future",
            Arrays.asList(
                field("futureStatus", FutureStatus.class, EntFieldKind.ENUM, false),
                field("futureValue", null, "FUTURE_KIND", false)
            ),
            Collections.<DocRelationModel>emptyList(),
            Collections.<DocIndexModel>emptyList(),
            false
        );

        Map<String, Object> document = new EntityDocumentationProjector().project(Collections.singletonList(future));
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("contractVersion", "entities")),
            document.keySet()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = ((List<Map<String, Object>>) document.get("entities")).get(0);
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("resourceCode", "name", "description", "fields", "relations", "indexes")),
            entity.keySet()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
        Map<String, Object> futureValue = findBy(fields, "property", "futureValue");
        Assertions.assertEquals("unknown", futureValue.get("type"));
        Assertions.assertEquals("FUTURE_KIND", futureValue.get("kind"));

        Map<String, Object> futureStatus = findBy(fields, "property", "futureStatus");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enumValues = (List<Map<String, Object>>) futureStatus.get("enumValues");
        Assertions.assertEquals(Arrays.asList("ACTIVE", "FUTURE"), values(enumValues, "name"));
    }

    private static DocEntityModel entity(
        Class<?> type,
        String resourceCode,
        List<DocFieldModel> fields,
        List<DocRelationModel> relations,
        List<DocIndexModel> indexes,
        boolean hidden
    ) {
        return new DocEntityModel(
            type,
            SourcedValue.explicit(resourceCode, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(resourceCode + " name", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit("description", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit("physical_table", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.unknown(null),
            SourcedValue.unknown(null),
            SourcedValue.explicit(Boolean.valueOf(hidden), MetaValueSource.NATIVE_EXPLICIT),
            Collections.singletonList("admin"),
            fields,
            relations,
            indexes
        );
    }

    private static DocFieldModel field(String property, Class<?> type, EntFieldKind kind, boolean hidden) {
        return field(property, type, kind == null ? null : kind.name(), hidden);
    }

    private static DocFieldModel field(String property, Class<?> type, String kind, boolean hidden) {
        return new DocFieldModel(
            property,
            type,
            SourcedValue.explicit("physical_" + property, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(property + " label", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit("field description", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit("example", MetaValueSource.NATIVE_EXPLICIT),
            Collections.singletonList("example"),
            SourcedValue.explicit(Boolean.TRUE, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(Boolean.FALSE, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(Integer.valueOf(80), MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.unknown(null),
            SourcedValue.explicit(kind, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.unknown(null),
            SourcedValue.explicit("secret default", MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.unknown(null),
            SourcedValue.unknown(null),
            SourcedValue.explicit(Boolean.valueOf(hidden), MetaValueSource.NATIVE_EXPLICIT),
            Collections.singletonList("admin"),
            Collections.emptyList()
        );
    }

    private static DocRelationModel relation(String field, String target, String sourceField, String targetField) {
        return new DocRelationModel(
            field,
            SourcedValue.unknown(null),
            SourcedValue.explicit(target, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(sourceField, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(targetField, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(RelationCardinality.MANY_TO_ONE, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(RelationOwnerSide.DECLARING_ENTITY, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.explicit(RelationResolutionStatus.RESOLVED, MetaValueSource.NATIVE_EXPLICIT),
            SourcedValue.unknown(null),
            SourcedValue.explicit("relation remark", MetaValueSource.NATIVE_EXPLICIT),
            false
        );
    }

    private static DocIndexModel index(String name, List<String> fields, boolean unique) {
        return new DocIndexModel(
            SourcedValue.explicit(name, MetaValueSource.NATIVE_EXPLICIT),
            fields,
            SourcedValue.explicit(Boolean.valueOf(unique), MetaValueSource.NATIVE_EXPLICIT)
        );
    }

    private static List<String> values(List<Map<String, Object>> items, String key) {
        java.util.ArrayList<String> values = new java.util.ArrayList<String>();
        for (Map<String, Object> item : items) {
            values.add(String.valueOf(item.get(key)));
        }
        return values;
    }

    private static Map<String, Object> findBy(List<Map<String, Object>> items, String key, String value) {
        for (Map<String, Object> item : items) {
            if (value.equals(item.get(key))) {
                return item;
            }
        }
        Assertions.fail("Missing item " + key + "=" + value);
        return Collections.emptyMap();
    }

    private static boolean contains(List<Map<String, Object>> items, String key, String value) {
        for (Map<String, Object> item : items) {
            if (value.equals(item.get(key))) {
                return true;
            }
        }
        return false;
    }

    private enum Status {
        DISABLED,
        ACTIVE
    }

    private enum FutureStatus {
        ACTIVE,
        FUTURE
    }

    private static final class Order {
    }

    private static final class Customer {
    }

    private static final class HiddenEntity {
    }

    private static final class FutureEntity {
    }
}
