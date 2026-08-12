package com.entloom.crud.core.runtime.model.parser;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudExportField;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudNativeRuntimeModelParserTest {

    @Test
    void should_include_reachable_multi_hop_edges_in_frozen_root_relation_graph() {
        EntityMetaRegistry registry = registry(RootEntity.class, MidEntity.class, LeafEntity.class);

        RelationGraph graph = registry.getRelationGraph(RootEntity.class);

        Assertions.assertEquals(0, graph.outgoingOf(RootEntity.class).size());
        Assertions.assertEquals(1, graph.outgoingOf(MidEntity.class).size());
        Assertions.assertEquals(1, graph.outgoingOf(LeafEntity.class).size());
    }

    @Test
    void should_include_parent_and_sibling_edges_when_root_is_child_entity() {
        EntityMetaRegistry registry = registry(BatchEntity.class, LineEntity.class, MediaEntity.class);

        RelationGraph graph = registry.getRelationGraph(LineEntity.class);
        List<RelationEdge> lineOutgoing = graph.outgoingOf(LineEntity.class);
        List<RelationEdge> mediaOutgoing = graph.outgoingOf(MediaEntity.class);

        Assertions.assertEquals(1, lineOutgoing.size());
        Assertions.assertEquals(BatchEntity.class, lineOutgoing.get(0).getToEntity());
        Assertions.assertEquals(1, mediaOutgoing.size());
        Assertions.assertEquals(BatchEntity.class, mediaOutgoing.get(0).getToEntity());
    }

    @Test
    void should_build_one_to_many_edge_from_collection_relation_field() {
        EntityMetaRegistry registry = registry(ParentEntity.class, ChildEntity.class);

        List<RelationEdge> outgoing = registry.getRelationGraph(ParentEntity.class).outgoingOf(ParentEntity.class);

        Assertions.assertEquals(1, outgoing.size());
        RelationEdge edge = outgoing.get(0);
        Assertions.assertEquals(ChildEntity.class, edge.getToEntity());
        Assertions.assertEquals("children", edge.getRelationField());
        Assertions.assertEquals("id", edge.getFromField());
        Assertions.assertEquals("parentId", edge.getToField());
        Assertions.assertEquals(RelationCardinality.ONE_TO_MANY, edge.getCardinality());
    }

    @Test
    void should_build_resource_descriptor_from_entity_meta() {
        EntityMetaRegistry registry = registry(NamedEntity.class);

        ResourceDescriptor descriptor = registry.getResourceDescriptor(NamedEntity.class);

        Assertions.assertEquals("named-resource", descriptor.getResourceCode());
        Assertions.assertEquals("owner-service", descriptor.getOwnerService());
        Assertions.assertTrue(descriptor.getAliases().contains(NamedEntity.class.getSimpleName()));
        Assertions.assertTrue(descriptor.getAliases().contains(NamedEntity.class.getName()));
        Assertions.assertEquals("named-resource", registry.getEntityMeta(NamedEntity.class).getEntityName());
    }

    @Test
    void should_build_export_field_metadata_from_annotation() {
        EntityMetaRegistry registry = registry(ExportMetaEntity.class);

        EntityFieldMeta fieldMeta = registry.getEntityMeta(ExportMetaEntity.class).resolveFieldMeta("studentId");

        Assertions.assertEquals(Boolean.FALSE, fieldMeta.getExportable());
        Assertions.assertEquals(Boolean.FALSE, fieldMeta.getExportDefaultVisible());
        Assertions.assertEquals("学生编号", fieldMeta.getExportLabel());
        Assertions.assertEquals("text", fieldMeta.getExportFormat());
        Assertions.assertEquals("student_status", fieldMeta.getDictionaryCode());
        Assertions.assertEquals("studentName", fieldMeta.getDisplayField());
    }

    private EntityMetaRegistry registry(Class<?>... entityTypes) {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Arrays.asList(entityTypes))
        );
        registry.validateOrThrow();
        return registry;
    }

    @EntCrudEntity
    private static class RootEntity {
        Long id;
    }

    @EntCrudEntity
    private static class MidEntity {
        Long id;
        @EntCrudField(targetClass = RootEntity.class, targetField = "id")
        Long rootId;
    }

    @EntCrudEntity
    private static class LeafEntity {
        Long id;
        @EntCrudField(targetClass = MidEntity.class, targetField = "id")
        Long midId;
    }

    @EntCrudEntity
    private static class BatchEntity {
        Long id;
    }

    @EntCrudEntity
    private static class LineEntity {
        Long id;
        @EntCrudField(targetClass = BatchEntity.class, targetField = "id")
        Long batchId;
    }

    @EntCrudEntity
    private static class MediaEntity {
        Long id;
        @EntCrudField(targetClass = BatchEntity.class, targetField = "id")
        Long batchId;
    }

    @EntCrudEntity
    private static class ParentEntity {
        Long id;

        @EntCrudField(
            targetClass = ChildEntity.class,
            sourceField = "id",
            targetField = "parentId",
            cardinality = RelationCardinality.ONE_TO_MANY
        )
        List<ChildEntity> children;
    }

    @EntCrudEntity
    private static class ChildEntity {
        Long id;
        Long parentId;
    }

    @EntCrudEntity(name = "named-resource", table = "t_named", ownerService = "owner-service")
    private static class NamedEntity {
        Long id;
    }

    @EntCrudEntity
    private static class ExportMetaEntity {
        Long id;

        @EntCrudExportField(
            exportable = false,
            defaultVisible = false,
            label = "学生编号",
            format = "text",
            dictionaryCode = "student_status",
            displayField = "studentName"
        )
        Long studentId;

        String studentName;
    }
}
