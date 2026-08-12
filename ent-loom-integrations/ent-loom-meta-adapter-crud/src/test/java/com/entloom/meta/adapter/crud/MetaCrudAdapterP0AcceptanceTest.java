package com.entloom.meta.adapter.crud;

import com.baomidou.mybatisplus.annotations.IdType;
import com.baomidou.mybatisplus.annotations.TableId;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeRelationModel;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.enums.EntFieldKind;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaCrudAdapterP0AcceptanceTest {

    @Test
    void p0_crud_acceptance_should_cover_meta_only_crud_only_override_and_relation_direction() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(
            MetaOnlyOrder.class,
            MetaOnlyCustomer.class,
            CrudOnlyOrder.class,
            CrudOnlyCustomer.class,
            OverrideOrder.class,
            OverrideCustomer.class
        ));
        CrudRuntimeModelBackedEntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(adapter.runtimeModel());

        EntityMeta metaOnlyOrder = registry.getEntityMeta(MetaOnlyOrder.class);
        Assertions.assertEquals("meta_only_order", metaOnlyOrder.getEntityName());
        Assertions.assertEquals("meta_only_order", metaOnlyOrder.getTable());
        Assertions.assertEquals("customer_id", metaOnlyOrder.resolveColumn("customerId"));
        RelationEdge metaOnlyEdge = findEdge(registry.getRelationGraph(MetaOnlyOrder.class).getEdges(), MetaOnlyCustomer.class);
        Assertions.assertSame(MetaOnlyOrder.class, metaOnlyEdge.getFromEntity());
        Assertions.assertSame(MetaOnlyCustomer.class, metaOnlyEdge.getToEntity());
        Assertions.assertEquals("customerId", metaOnlyEdge.getFromField());
        Assertions.assertEquals("id", metaOnlyEdge.getToField());
        CrudRuntimeModel runtimeModel = adapter.runtimeModel();
        CrudRuntimeEntityModel metaOnlyRuntimeOrder = runtimeModel.getEntity(MetaOnlyOrder.class);
        Assertions.assertEquals("meta_only_order", metaOnlyRuntimeOrder.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("customer_id", metaOnlyRuntimeOrder.getField("customerId").getColumnName());
        Assertions.assertEquals(MetaOnlyOrder.class, runtimeModel.resolveEntityType("meta_only_order"));

        EntityMeta crudOnlyOrder = registry.getEntityMeta(CrudOnlyOrder.class);
        Assertions.assertEquals("crud_only_order", crudOnlyOrder.getEntityName());
        Assertions.assertEquals("crud_only_order_table", crudOnlyOrder.getTable());
        RelationEdge crudOnlyEdge = findEdge(registry.getRelationGraph(CrudOnlyOrder.class).getEdges(), CrudOnlyCustomer.class);
        Assertions.assertSame(CrudOnlyOrder.class, crudOnlyEdge.getFromEntity());
        Assertions.assertSame(CrudOnlyCustomer.class, crudOnlyEdge.getToEntity());
        CrudRuntimeEntityModel crudOnlyRuntimeOrder = runtimeModel.getEntity(CrudOnlyOrder.class);
        Assertions.assertEquals("crud_only_order", crudOnlyRuntimeOrder.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("crud_only_order_table", crudOnlyRuntimeOrder.getTable());

        EntityMeta overrideOrder = registry.getEntityMeta(OverrideOrder.class);
        Assertions.assertEquals("override_order_native", overrideOrder.getEntityName());
        Assertions.assertEquals("override_order_table", overrideOrder.getTable());
        RelationEdge overrideEdge = findEdge(registry.getRelationGraph(OverrideOrder.class).getEdges(), OverrideCustomer.class);
        Assertions.assertEquals("customerCode", overrideEdge.getToField());
        Assertions.assertEquals(RelationCardinality.MANY_TO_ONE, overrideEdge.getCardinality());
        CrudRuntimeEntityModel overrideRuntimeOrder = runtimeModel.getEntity(OverrideOrder.class);
        Assertions.assertEquals("override_order_native", overrideRuntimeOrder.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("override_order_table", overrideRuntimeOrder.getTable());
        CrudRuntimeRelationModel overrideRuntimeEdge = findRuntimeEdge(runtimeModel.getRelations(), OverrideCustomer.class);
        Assertions.assertEquals("customerCode", overrideRuntimeEdge.getToField());
        Assertions.assertTrue(hasDiagnostic(adapter.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, "targetField"));
    }

    @Test
    void p0_crud_acceptance_should_fail_fast_on_relation_target_field_diagnostic() {
        MetaDiagnosticException exception = Assertions.assertThrows(
            MetaDiagnosticException.class,
            () -> new MetaCrudAdapter(Arrays.<Class<?>>asList(BrokenTargetFieldOrder.class, BrokenCustomer.class))
        );

        Assertions.assertTrue(hasDiagnostic(exception.diagnostics(), MetaDiagnosticCode.RELATION_TARGET_FIELD_NOT_FOUND, "targetField"));
    }

    @Test
    void p0_crud_acceptance_should_map_mybatis_auto_id_to_generated_policy() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Collections.<Class<?>>singletonList(MybatisAutoIdOrder.class));
        CrudRuntimeModelBackedEntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(adapter.runtimeModel());

        EntityMeta order = registry.getEntityMeta(MybatisAutoIdOrder.class);

        Assertions.assertEquals(EntityIdPolicy.GENERATED, order.getIdPolicy());
        Assertions.assertEquals(EntityIdPolicy.GENERATED, adapter.runtimeModel().getEntity(MybatisAutoIdOrder.class).getIdentity().getIdPolicy());
    }

    @Test
    void p0_crud_acceptance_should_not_expose_legacy_relation_names() {
        Assertions.assertFalse(hasMethod(EntCrudField.class, "refEntity"));
        Assertions.assertFalse(hasMethod(EntCrudField.class, "relationEntity"));
        Assertions.assertFalse(hasMethod(EntCrudField.class, "relationEntityEn"));
        Assertions.assertFalse(hasMethod(EntCrudField.class, "localField"));
        Assertions.assertFalse(hasMethod(EntCrudField.class, "refField"));
    }

    private RelationEdge findEdge(Collection<RelationEdge> edges, Class<?> targetType) {
        for (RelationEdge edge : edges) {
            if (targetType.equals(edge.getToEntity())) {
                return edge;
            }
        }
        Assertions.fail("Missing edge to " + targetType.getName());
        return null;
    }

    private CrudRuntimeRelationModel findRuntimeEdge(Collection<CrudRuntimeRelationModel> edges, Class<?> targetType) {
        for (CrudRuntimeRelationModel edge : edges) {
            if (targetType.equals(edge.getToEntity())) {
                return edge;
            }
        }
        Assertions.fail("Missing runtime edge to " + targetType.getName());
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

    private boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

    @EntEntity(entity = "meta_only_order")
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

    @EntCrudEntity(name = "crud_only_order", table = "crud_only_order_table")
    private static final class CrudOnlyOrder {
        private Long id;

        @EntCrudField(targetClass = CrudOnlyCustomer.class)
        private Long customerId;
    }

    @EntCrudEntity(name = "crud_only_customer", table = "crud_only_customer")
    private static final class CrudOnlyCustomer {
        private Long id;
    }

    @EntEntity(entity = "override_order")
    @EntCrudEntity(name = "override_order_native", table = "override_order_table")
    private static final class OverrideOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "override_customer", targetField = "idCode")
        @EntCrudField(targetClass = OverrideCustomer.class, targetField = "customerCode")
        private Long customerId;
    }

    @EntEntity(entity = "override_customer")
    @EntCrudEntity(name = "override_customer", table = "override_customer")
    private static final class OverrideCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;

        private String customerCode;

        private String idCode;
    }

    @EntEntity(entity = "broken_target_field_order")
    private static final class BrokenTargetFieldOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "broken_customer", targetField = "missingCode")
        private Long customerId;
    }

    @EntEntity(entity = "broken_customer")
    private static final class BrokenCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;
    }

    @EntEntity(entity = "mybatis_auto_id_order")
    private static final class MybatisAutoIdOrder extends MybatisAutoIdBase {
        @EntField(EntFieldKind.TEXT)
        private String orderNo;
    }

    private static class MybatisAutoIdBase {
        @TableId(type = IdType.AUTO)
        @EntField(EntFieldKind.ID)
        private Long id;
    }
}
