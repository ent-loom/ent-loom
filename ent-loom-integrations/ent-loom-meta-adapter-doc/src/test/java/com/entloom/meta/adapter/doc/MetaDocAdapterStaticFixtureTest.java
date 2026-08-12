package com.entloom.meta.adapter.doc;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.spi.DocIndexProvider;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.meta.adapter.doc.merge.DocRuntimeModelMerger;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.enums.EntFieldKind;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaDocAdapterStaticFixtureTest {

    @Test
    void order_static_fixture_should_output_entity_fields_relations_indexes_and_final_map_only_at_boundary() {
        MetaDocAdapter adapter = new MetaDocAdapter(
            new SimpleDocMetaResolver(),
            Arrays.<Class<?>>asList(Order.class, OrderItem.class, Customer.class)
        );

        Map<String, Object> doc = adapter.buildOne(Order.class);
        Assertions.assertEquals("order", doc.get("resourceCode"));
        Assertions.assertEquals("订单文档覆盖", doc.get("entityName"));
        Assertions.assertEquals("sales_order", doc.get("tableName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) doc.get("fields");
        Assertions.assertEquals(3, fields.size());
        Map<String, Object> orderNo = findBy(fields, "property", "orderNo");
        Assertions.assertEquals("订单号文档覆盖", orderNo.get("name"));
        Assertions.assertEquals("SO-001", orderNo.get("example"));
        Assertions.assertEquals(Boolean.TRUE, orderNo.get("required"));
        Assertions.assertEquals("order_no", orderNo.get("column"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) doc.get("relations");
        Assertions.assertEquals(2, relations.size());
        Map<String, Object> customerRelation = findBy(relations, "relationField", "customerId");
        Assertions.assertEquals("customer", customerRelation.get("targetEntity"));
        Assertions.assertEquals("customerId", customerRelation.get("sourceField"));
        Assertions.assertEquals("id", customerRelation.get("targetField"));
        Assertions.assertEquals("客户", customerRelation.get("targetEntityLabel"));

        Map<String, Object> itemRelation = findBy(relations, "relationField", "items");
        Assertions.assertEquals("order_item", itemRelation.get("targetEntity"));
        Assertions.assertEquals("items", itemRelation.get("sourceField"));
        Assertions.assertEquals("orderId", itemRelation.get("targetField"));
        Assertions.assertEquals(RelationCardinality.ONE_TO_MANY.name(), itemRelation.get("cardinality"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indexes = (List<Map<String, Object>>) doc.get("indexes");
        Assertions.assertEquals(1, indexes.size());
        Assertions.assertEquals("uk_order_no", indexes.get(0).get("name"));
        Assertions.assertEquals(Boolean.TRUE, indexes.get(0).get("unique"));
        Assertions.assertFalse(hasError(adapter.diagnostics()));
        Assertions.assertFalse(adapter.models().isEmpty());
    }

    @Test
    void business_doc_provider_should_override_display_fields_and_hide_fields() {
        MetaDocAdapter adapter = new MetaDocAdapter(
            new SimpleDocMetaResolver(),
            DocIndexProvider.noop(),
            Arrays.<Class<?>>asList(Order.class, OrderItem.class, Customer.class),
            new com.entloom.meta.core.parser.ReflectiveEntMetaParser(),
            new DocRuntimeModelMerger(),
            new BusinessDocOverrideProvider(),
            DefaultMetaDiagnosticPolicy.lenient()
        );

        Map<String, Object> doc = adapter.buildOne(Order.class);
        Assertions.assertEquals("业务订单文档", doc.get("entityName"));
        Assertions.assertEquals("交易", doc.get("group"));
        Assertions.assertEquals("业务侧补充说明", doc.get("remark"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) doc.get("fields");
        Assertions.assertEquals(2, fields.size());
        Map<String, Object> orderNo = findBy(fields, "property", "orderNo");
        Assertions.assertEquals("业务订单号", orderNo.get("name"));
        Assertions.assertEquals("BO-001", orderNo.get("example"));
        Assertions.assertEquals("基础信息", orderNo.get("group"));
        Assertions.assertEquals("业务字段备注", orderNo.get("remark"));
        Assertions.assertEquals(Arrays.asList("admin", "operator"), orderNo.get("visibleFor"));
        Assertions.assertFalse(containsBy(fields, "property", "customerId"));
        Assertions.assertTrue(hasWarn(adapter.diagnostics()));
    }

    private Map<String, Object> findBy(List<Map<String, Object>> items, String key, String value) {
        for (Map<String, Object> item : items) {
            if (value.equals(item.get(key))) {
                return item;
            }
        }
        Assertions.fail("Missing item " + key + "=" + value);
        return null;
    }

    private boolean hasError(List<MetaDiagnostic> diagnostics) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.level() == MetaDiagnosticLevel.ERROR) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWarn(List<MetaDiagnostic> diagnostics) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.level() == MetaDiagnosticLevel.WARN) {
                return true;
            }
        }
        return false;
    }

    private boolean containsBy(List<Map<String, Object>> items, String key, String value) {
        for (Map<String, Object> item : items) {
            if (value.equals(item.get(key))) {
                return true;
            }
        }
        return false;
    }

    @EntEntity(entity = "order", label = "订单")
    @EntIndex(name = "uk_order_no", fields = {"orderNo"}, unique = true)
    @EntDocEntity(name = "订单文档覆盖")
    private static final class Order {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(value = EntFieldKind.TEXT, label = "订单号", required = OptionalBoolean.TRUE)
        @EntDocField(name = "订单号文档覆盖", example = "SO-001")
        private String orderNo;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "customer")
        @EntDocField(targetEntityLabel = "客户", relationRemark = "订单归属客户")
        private Long customerId;

        @EntRelation(targetEntity = "order_item", targetField = "orderId", cardinality = com.entloom.meta.enums.RelationCardinality.ONE_TO_MANY)
        private List<OrderItem> items;
    }

    @EntEntity(entity = "order_item")
    private static final class OrderItem {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        private Long orderId;
    }

    @EntEntity(entity = "customer")
    private static final class Customer {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.TEXT)
        private String name;
    }

    private static final class SimpleDocMetaResolver implements DocEntityMetaResolver {
        @Override
        public String resolveTableName(Class<?> entityClass, String configuredTableName) {
            if (configuredTableName != null && !configuredTableName.trim().isEmpty()) {
                return configuredTableName;
            }
            if (entityClass == Order.class) {
                return "sales_order";
            }
            return camelToSnake(entityClass.getSimpleName());
        }

        @Override
        public String resolveColumn(Class<?> entityClass, String property) {
            return camelToSnake(property);
        }

        private String camelToSnake(String value) {
            if (value == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isUpperCase(c) && i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            }
            return builder.toString();
        }
    }

    private static final class BusinessDocOverrideProvider implements DocOverrideProvider {
        @Override
        public DocEntityOverride overrideFor(Class<?> entityClass, String resourceCode) {
            if (entityClass != Order.class) {
                return null;
            }
            return DocEntityOverride.builder()
                .entityName("业务订单文档")
                .group("交易")
                .remark("业务侧补充说明")
                .field(DocFieldOverride.builder("orderNo")
                    .name("业务订单号")
                    .example("BO-001")
                    .group("基础信息")
                    .remark("业务字段备注")
                    .visibleFor(Arrays.asList("admin", "operator"))
                    .build())
                .field(DocFieldOverride.builder("customerId")
                    .hidden(Boolean.TRUE)
                    .build())
                .build();
        }
    }
}
