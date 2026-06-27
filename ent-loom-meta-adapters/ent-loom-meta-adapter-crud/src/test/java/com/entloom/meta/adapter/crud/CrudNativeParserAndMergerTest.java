package com.entloom.meta.adapter.crud;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.adapter.crud.merge.CrudRuntimeModelMerger;
import com.entloom.meta.adapter.crud.model.CrudEntityRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudNativeEntityModel;
import com.entloom.meta.adapter.crud.model.CrudNativeRelationModel;
import com.entloom.meta.adapter.crud.model.CrudRelationRuntimeModel;
import com.entloom.meta.adapter.crud.model.CrudRuntimeProperties;
import com.entloom.meta.adapter.crud.parser.CrudNativeAnnotationParser;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.EntFieldKind;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudNativeParserAndMergerTest {

    @Test
    void native_parser_should_mark_annotation_defaults_as_unknown_not_explicit() {
        CrudNativeEntityModel model = new CrudNativeAnnotationParser()
            .parseWithDiagnostics(NativeOrder.class)
            .value();

        Assertions.assertNotNull(model);
        Assertions.assertEquals("native_order", model.resourceCode().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, model.resourceCode().source());
        Assertions.assertEquals("id", model.idField().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, model.idField().source());

        CrudNativeRelationModel relation = model.relations().get(0);
        Assertions.assertSame(NativeCustomer.class, relation.targetClass().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, relation.targetClass().source());
        Assertions.assertEquals("customerId", relation.sourceField().value());
        Assertions.assertEquals(MetaValueSource.INFERRED, relation.sourceField().source());
        Assertions.assertEquals("id", relation.targetField().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.targetField().source());
        Assertions.assertEquals(RelationCardinality.MANY_TO_ONE, relation.cardinality().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.cardinality().source());
        Assertions.assertEquals(RelationScope.LOCAL_DB, relation.scope().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.scope().source());
        Assertions.assertEquals(JoinType.LEFT, relation.joinType().value());
        Assertions.assertEquals(MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, relation.joinType().source());
    }

    @Test
    void merger_should_support_meta_only_crud_only_and_native_explicit_override() {
        CrudRuntimeModelMerger merger = new CrudRuntimeModelMerger();
        CrudNativeAnnotationParser nativeParser = new CrudNativeAnnotationParser();
        ReflectiveEntMetaParser metaParser = new ReflectiveEntMetaParser();

        MetaDiagnosticResult<CrudEntityRuntimeModel> metaOnly = merger.merge(
            MetaOnlyOrder.class,
            metaParser.parse(MetaOnlyOrder.class),
            null
        );
        Assertions.assertEquals("meta_only_order", metaOnly.value().resourceCode().value());
        Assertions.assertEquals("id", metaOnly.value().idField().value());

        MetaDiagnosticResult<CrudEntityRuntimeModel> crudOnly = merger.merge(
            NativeOrder.class,
            null,
            nativeParser.parseWithDiagnostics(NativeOrder.class).value()
        );
        Assertions.assertEquals("native_order", crudOnly.value().resourceCode().value());
        Assertions.assertEquals("native_order_table", crudOnly.value().table().value());
        Assertions.assertEquals(3, crudOnly.value().fields().size());

        EntEntityDescriptor meta = metaParser.parse(MergeOrder.class);
        CrudNativeEntityModel nativeModel = nativeParser.parseWithDiagnostics(MergeOrder.class).value();
        MetaDiagnosticResult<CrudEntityRuntimeModel> merged = merger.merge(MergeOrder.class, meta, nativeModel);

        Assertions.assertEquals("merge_order_native", merged.value().resourceCode().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, merged.value().resourceCode().source());
        CrudRelationRuntimeModel relation = merged.value().relations().get(0);
        Assertions.assertEquals("customerCode", relation.targetField().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, relation.targetField().source());
        Assertions.assertEquals("customerId", relation.relationField());
        Assertions.assertEquals("buyerId", relation.sourceField().value());
        Assertions.assertEquals(MetaValueSource.NATIVE_EXPLICIT, relation.sourceField().source());
        Assertions.assertTrue(hasDiagnostic(merged.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, CrudRuntimeProperties.TARGET_FIELD));
        Assertions.assertTrue(hasDiagnostic(merged.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, CrudRuntimeProperties.SOURCE_FIELD));
    }

    private boolean hasDiagnostic(List<MetaDiagnostic> diagnostics, MetaDiagnosticCode code, String property) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code && property.equals(diagnostic.property())) {
                return true;
            }
        }
        return false;
    }

    @EntCrudEntity(name = "native_order", table = "native_order_table")
    private static final class NativeOrder {
        private Long id;
        private String orderNo;

        @EntCrudField(targetClass = NativeCustomer.class)
        private Long customerId;
    }

    @EntCrudEntity(name = "native_customer", table = "native_customer")
    private static final class NativeCustomer {
        private Long id;
    }

    @EntEntity(entity = "meta_only_order")
    private static final class MetaOnlyOrder {
        @EntField(EntFieldKind.ID)
        private Long id;
    }

    @EntEntity(entity = "merge_order")
    @EntCrudEntity(name = "merge_order_native")
    private static final class MergeOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "merge_customer", sourceField = "customerId", targetField = "idCode")
        @EntCrudField(targetClass = MergeCustomer.class, sourceField = "buyerId", targetField = "customerCode")
        private Long customerId;

        private Long buyerId;
    }

    @EntEntity(entity = "merge_customer")
    @EntCrudEntity(name = "merge_customer")
    private static final class MergeCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;

        private String idCode;

        private String customerCode;
    }
}
