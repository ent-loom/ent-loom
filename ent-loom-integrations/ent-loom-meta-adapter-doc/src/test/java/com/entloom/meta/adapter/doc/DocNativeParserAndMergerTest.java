package com.entloom.meta.adapter.doc;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocRelationModel;
import com.entloom.doc.core.model.DocRuntimeProperties;
import com.entloom.doc.core.parser.DocNativeAnnotationParser;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.enums.EntFieldKind;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DocNativeParserAndMergerTest {

    @Test
    void native_parser_should_keep_doc_annotation_defaults_non_explicit() {
        DocEntityModel model = new DocNativeAnnotationParser(new SimpleDocMetaResolver(), null)
            .parseWithDiagnostics(NativeOrder.class)
            .value();

        Assertions.assertNotNull(model);
        Assertions.assertEquals("native_order", model.entityName().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, model.entityName().source());
        Assertions.assertEquals("customerId", model.fields().get(0).property());
        Assertions.assertNull(model.fields().get(0).required().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, model.fields().get(0).required().source());

        DocRelationModel relation = model.relations().get(0);
        Assertions.assertEquals("customerId", relation.sourceField().value());
        Assertions.assertEquals(MetaValueSource.INFERRED, relation.sourceField().source());
        Assertions.assertEquals("id", relation.targetField().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.targetField().source());
        Assertions.assertEquals(RelationCardinality.MANY_TO_ONE, relation.cardinality().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.cardinality().source());
    }

    @Test
    void native_parser_should_emit_stable_field_model_for_doc_only_entity_without_field_annotations() {
        DocEntityModel model = new DocNativeAnnotationParser(new SimpleDocMetaResolver(), null)
            .parseWithDiagnostics(DocOnlyCustomer.class)
            .value();

        Assertions.assertNotNull(model);
        Assertions.assertEquals(2, model.fields().size());
        Assertions.assertEquals("customerName", model.fields().get(1).property());
        Assertions.assertEquals("customer_name", model.fields().get(1).column().value());
        Assertions.assertEquals(MetaValueSource.INFERRED, model.fields().get(1).column().source());
    }

    @Test
    void merger_should_warn_when_doc_native_explicit_value_overrides_meta() {
        MetaDocAdapter adapter = new MetaDocAdapter(
            new SimpleDocMetaResolver(),
            java.util.Arrays.<Class<?>>asList(MergeOrder.class, MergeCustomer.class)
        );

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> relations = (List<java.util.Map<String, Object>>) adapter.buildOne(MergeOrder.class).get("relations");

        Assertions.assertEquals("customerCode", relations.get(0).get("targetField"));
        Assertions.assertTrue(hasDiagnostic(adapter.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, DocRuntimeProperties.TARGET_FIELD));
    }

    private boolean hasDiagnostic(List<MetaDiagnostic> diagnostics, MetaDiagnosticCode code, String property) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code && property.equals(diagnostic.property())) {
                return true;
            }
        }
        return false;
    }

    @EntDocEntity(name = "native_order")
    private static final class NativeOrder {
        @EntDocField(targetEntity = "native_customer")
        private Long customerId;
    }

    @EntDocEntity(name = "doc_only_customer")
    private static final class DocOnlyCustomer {
        private Long id;
        private String customerName;
    }

    @EntEntity(entity = "merge_order")
    private static final class MergeOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "merge_customer", targetField = "idCode")
        @EntDocField(targetField = "customerCode")
        private Long customerId;
    }

    @EntEntity(entity = "merge_customer")
    private static final class MergeCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;

        private String idCode;

        private String customerCode;
    }

    private static final class SimpleDocMetaResolver implements DocEntityMetaResolver {
        @Override
        public String resolveTableName(Class<?> entityClass, String configuredTableName) {
            if (configuredTableName != null && !configuredTableName.trim().isEmpty()) {
                return configuredTableName;
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
}
