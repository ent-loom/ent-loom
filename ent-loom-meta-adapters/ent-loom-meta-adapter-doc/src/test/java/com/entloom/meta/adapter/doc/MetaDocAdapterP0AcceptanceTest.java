package com.entloom.meta.adapter.doc;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.enums.EntFieldKind;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaDocAdapterP0AcceptanceTest {

    @Test
    void p0_doc_acceptance_should_cover_meta_only_doc_only_override_relations_and_indexes() {
        MetaDocAdapter adapter = new MetaDocAdapter(
            new SimpleDocMetaResolver(),
            Arrays.<Class<?>>asList(MetaOnlyOrder.class, MetaOnlyCustomer.class, DocOnlyOrder.class, OverrideOrder.class, OverrideCustomer.class)
        );

        Map<String, Object> metaDoc = adapter.buildOne(MetaOnlyOrder.class);
        Assertions.assertEquals("meta_only_order", metaDoc.get("resourceCode"));
        Assertions.assertEquals("meta_only_order", metaDoc.get("tableName"));
        Assertions.assertEquals(1, relationDocs(metaDoc).size());
        Assertions.assertEquals("meta_only_customer", relationDocs(metaDoc).get(0).get("targetEntity"));
        Assertions.assertEquals("customerId", relationDocs(metaDoc).get(0).get("sourceField"));
        Assertions.assertEquals("id", relationDocs(metaDoc).get(0).get("targetField"));
        Assertions.assertEquals(1, indexDocs(metaDoc).size());
        Assertions.assertEquals("idx_meta_order_customer", indexDocs(metaDoc).get(0).get("name"));

        Map<String, Object> docOnly = adapter.buildOne(DocOnlyOrder.class);
        Assertions.assertEquals("doc_only_order_name", docOnly.get("entityName"));
        Assertions.assertEquals("doc_only_order", docOnly.get("tableName"));
        Assertions.assertEquals("订单号", findBy(fieldDocs(docOnly), "property", "orderNo").get("name"));
        Assertions.assertEquals(Boolean.TRUE, findBy(fieldDocs(docOnly), "property", "orderNo").get("required"));

        Map<String, Object> overrideDoc = adapter.buildOne(OverrideOrder.class);
        Assertions.assertEquals("override_order", overrideDoc.get("resourceCode"));
        Assertions.assertEquals("客户编号", findBy(fieldDocs(overrideDoc), "property", "customerId").get("name"));
        Assertions.assertEquals("customerCode", relationDocs(overrideDoc).get(0).get("targetField"));
        Assertions.assertEquals("客户", relationDocs(overrideDoc).get(0).get("targetEntityLabel"));
        Assertions.assertTrue(hasDiagnostic(adapter.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, "targetField"));
    }

    @Test
    void p0_doc_acceptance_should_fail_fast_on_missing_relation_target() {
        MetaDiagnosticException exception = Assertions.assertThrows(
            MetaDiagnosticException.class,
            () -> new MetaDocAdapter(new SimpleDocMetaResolver(), Arrays.<Class<?>>asList(BrokenOrder.class))
        );

        Assertions.assertTrue(hasDiagnostic(exception.diagnostics(), MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND, "targetEntity"));
    }

    @Test
    void p0_doc_acceptance_should_not_expose_legacy_relation_names() {
        Assertions.assertFalse(hasMethod(EntDocField.class, "refEntity"));
        Assertions.assertFalse(hasMethod(EntDocField.class, "relationEntity"));
        Assertions.assertFalse(hasMethod(EntDocField.class, "relationEntityName"));
        Assertions.assertFalse(hasMethod(EntDocField.class, "localField"));
        Assertions.assertFalse(hasMethod(EntDocField.class, "refField"));
        Assertions.assertSame(OptionalBoolean.class, method(EntDocField.class, "required").getReturnType());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldDocs(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("fields");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> relationDocs(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("relations");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> indexDocs(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("indexes");
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

    private boolean hasDiagnostic(Iterable<MetaDiagnostic> diagnostics, MetaDiagnosticCode code, String property) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code && property.equals(diagnostic.property())) {
                return true;
            }
        }
        return false;
    }

    private Method method(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }
        Assertions.fail("Missing method: " + methodName);
        return null;
    }

    private boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

    @EntEntity(entity = "meta_only_order")
    @EntIndex(name = "idx_meta_order_customer", fields = {"customerId"})
    private static final class MetaOnlyOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "meta_only_customer")
        private Long customerId;
    }

    @EntEntity(entity = "meta_only_customer")
    private static final class MetaOnlyCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;
    }

    @EntDocEntity(name = "doc_only_order_name")
    private static final class DocOnlyOrder {
        private Long id;

        @EntDocField(name = "订单号", required = OptionalBoolean.TRUE)
        private String orderNo;
    }

    @EntEntity(entity = "override_order")
    private static final class OverrideOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "override_customer", targetField = "idCode")
        @EntDocField(name = "客户编号", targetField = "customerCode", targetEntityLabel = "客户")
        private Long customerId;
    }

    @EntEntity(entity = "override_customer")
    private static final class OverrideCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;

        private String customerCode;

        private String idCode;
    }

    @EntEntity(entity = "broken_order")
    private static final class BrokenOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "missing_customer", cardinality = com.entloom.meta.enums.RelationCardinality.MANY_TO_ONE)
        private Long customerId;
    }

    private static final class SimpleDocMetaResolver implements DocEntityMetaResolver {
        @Override
        public String resolveTableName(Class<?> entityClass, String configuredTableName) {
            if (configuredTableName != null && !configuredTableName.trim().isEmpty()) {
                return configuredTableName;
            }
            String simpleName = entityClass.getSimpleName();
            if (simpleName.endsWith("Entity")) {
                simpleName = simpleName.substring(0, simpleName.length() - "Entity".length());
            }
            return camelToSnake(simpleName);
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
}
