package com.entloom.crud.core.runtime.model.parser;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.read.relation.PathResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeRelationModel;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRelationMappingResolverTest {
    @Test
    void 原生模式复用唯一反向引用并保持普通查询不展开() {
        for (List<Class<?>> types : Arrays.asList(Arrays.<Class<?>>asList(Parent.class, Child.class),
            Arrays.<Class<?>>asList(Child.class, Parent.class))) {
            EntityMetaRegistry registry = registry(types);
            RelationEdge edge = registry.getRelationGraph(Parent.class).outgoingOf(Parent.class).get(0);
            assertEquals("lines", edge.getRelationField());
            assertEquals("code", edge.getFromField());
            assertEquals("parentCode", edge.getToField());
            assertEquals(RelationCardinality.ONE_TO_MANY, edge.getCardinality());
            assertEquals("crud.relation.collection.unique-reverse", edge.getInferenceRuleId());
            assertNull(registry.getEntityMeta(Parent.class).resolveFieldMeta("lines"));
            QuerySpec<Object> plain = QuerySpec.builder().rootType(Parent.class).build();
            PathResolver resolver = new PathResolver(registry);
            assertTrue(resolver.resolve(plain, registry.getRelationGraph(Parent.class)).getExpandEdges().isEmpty());
            assertEquals(1, resolver.resolve(plain.toBuilder().expandRelations(Collections.singletonList("lines")).build(),
                registry.getRelationGraph(Parent.class)).getExpandEdges().size());
        }
    }

    @Test
    void 多个反向外键不猜测且显式展开拒绝() {
        EntityMetaRegistry registry = registry(Arrays.<Class<?>>asList(AmbiguousParent.class, AmbiguousChild.class));
        assertTrue(registry.getRelationGraph(AmbiguousParent.class).outgoingOf(AmbiguousParent.class).isEmpty());
        assertThrows(ValidationException.class, () -> new PathResolver(registry).resolve(
            QuerySpec.builder().rootType(AmbiguousParent.class).expandRelations(Collections.singletonList("lines")).build(),
            registry.getRelationGraph(AmbiguousParent.class)));
    }

    @Test
    void 显式映射可消除歧义且不会再生成重复映射() {
        EntityMetaRegistry registry = registry(Arrays.<Class<?>>asList(ExplicitParent.class, ExplicitChild.class));
        List<RelationEdge> edges = registry.getRelationGraph(ExplicitParent.class).outgoingOf(ExplicitParent.class);
        assertEquals(1, edges.size());
        assertEquals("secondaryId", edges.get(0).getToField());
    }

    @Test
    void 没有关系事实时不凭名称或集合类型建立关系() {
        EntityMetaRegistry registry = registry(Arrays.<Class<?>>asList(UnrelatedParent.class, UnrelatedChild.class));
        assertTrue(registry.getRelationGraph(UnrelatedParent.class).getEdges().isEmpty());
    }

    @Test
    void 泛型未知和临时集合不生成映射() {
        EntityMetaRegistry registry = registry(Arrays.<Class<?>>asList(UnresolvedParent.class, UnresolvedChild.class));
        assertTrue(registry.getRelationGraph(UnresolvedParent.class).outgoingOf(UnresolvedParent.class).isEmpty());
    }

    @Test
    void 一对一引用不反推一对多集合() {
        EntityMetaRegistry registry = registry(Arrays.<Class<?>>asList(UniqueParent.class, UniqueChild.class));
        assertTrue(registry.getRelationGraph(UniqueParent.class).outgoingOf(UniqueParent.class).isEmpty());
    }

    @Test
    void 模型合并新增反向引用时重新裁定而不沿用旧推导() {
        CrudRuntimeModel initial = new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Parent.class, Child.class));
        List<CrudRuntimeRelationModel> relations = new ArrayList<>(initial.getRelations());
        relations.add(new CrudRuntimeRelationModel(Child.class, Parent.class, "alternateCode", "alternateCode", "code",
            RelationScope.LOCAL_DB, JoinType.LEFT, RelationCardinality.MANY_TO_ONE));
        CrudRuntimeModel merged = new CrudRuntimeModel(initial.getEntities().values(), relations);
        assertTrue(merged.getRelations().stream().noneMatch(relation -> relation.getFromEntity() == Parent.class));

        relations.add(new CrudRuntimeRelationModel(Parent.class, Child.class, "lines", "code", "alternateCode",
            RelationScope.LOCAL_DB, JoinType.LEFT, RelationCardinality.ONE_TO_MANY));
        CrudRuntimeModel overridden = new CrudRuntimeModel(initial.getEntities().values(), relations);
        assertEquals(1, overridden.getRelations().stream().filter(relation -> relation.getFromEntity() == Parent.class).count());
        assertEquals("alternateCode", overridden.getRelations().stream()
            .filter(relation -> relation.getFromEntity() == Parent.class).findFirst().orElseThrow().getToField());
    }

    @Test
    void 模型重复构建与关系边往返保持来源且不重复补全() {
        CrudRuntimeModel initial = new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(Parent.class, Child.class));
        CrudRuntimeModel copied = new CrudRuntimeModel(initial.getEntities().values(), initial.getRelations());
        assertEquals(2, copied.getRelations().size());
        CrudRuntimeModel roundTrip = CrudRuntimeModel.from(new CrudRuntimeModelBackedEntityMetaRegistry(copied).getEntityMetas(),
            copied.relationEdges());
        assertEquals(2, roundTrip.getRelations().size());
        assertEquals("crud.relation.collection.unique-reverse", roundTrip.getRelations().stream()
            .filter(relation -> relation.getFromEntity() == Parent.class).findFirst().orElseThrow().getInferenceRuleId());
    }

    private EntityMetaRegistry registry(List<Class<?>> types) {
        return new CrudRuntimeModelBackedEntityMetaRegistry(new CrudNativeRuntimeModelParser().parse(types));
    }

    private static class ParentBase {
        Long id;
        String code;
        List<Child> lines;
    }

    @EntCrudEntity
    private static class Parent extends ParentBase {
    }

    @EntCrudEntity
    private static class Child {
        Long id;
        String alternateCode;
        @EntCrudField(targetClass = Parent.class, targetField = "code")
        String parentCode;
    }

    @EntCrudEntity
    private static class AmbiguousParent {
        Long id;
        List<AmbiguousChild> lines;
    }

    @EntCrudEntity
    private static class AmbiguousChild {
        Long id;
        @EntCrudField(targetClass = AmbiguousParent.class)
        Long primaryId;
        @EntCrudField(targetClass = AmbiguousParent.class)
        Long secondaryId;
    }

    @EntCrudEntity
    private static class ExplicitParent {
        Long id;
        @EntCrudField(targetClass = ExplicitChild.class, targetField = "secondaryId",
            cardinality = RelationCardinality.ONE_TO_MANY)
        List<ExplicitChild> lines;
    }

    @EntCrudEntity
    private static class ExplicitChild {
        Long id;
        @EntCrudField(targetClass = ExplicitParent.class)
        Long primaryId;
        @EntCrudField(targetClass = ExplicitParent.class)
        Long secondaryId;
    }

    @EntCrudEntity
    private static class UnrelatedParent {
        Long id;
        List<UnrelatedChild> lines;
    }

    @EntCrudEntity
    private static class UnrelatedChild {
        Long id;
        Long parentId;
    }

    @EntCrudEntity
    private static class UnresolvedParent {
        Long id;
        List<?> unknown;
        transient List<UnresolvedChild> temporary;
        // 泛型不明确时不能猜测；这个字段仅用于验证原始集合。
        @SuppressWarnings("rawtypes")
        List raw;
    }

    @EntCrudEntity
    private static class UnresolvedChild {
        Long id;
        @EntCrudField(targetClass = UnresolvedParent.class)
        Long parentId;
    }

    @EntCrudEntity
    private static class UniqueParent {
        Long id;
        List<UniqueChild> lines;
    }

    @EntCrudEntity
    private static class UniqueChild {
        Long id;
        @EntCrudField(targetClass = UniqueParent.class, cardinality = RelationCardinality.ONE_TO_ONE)
        Long parentId;
    }
}
