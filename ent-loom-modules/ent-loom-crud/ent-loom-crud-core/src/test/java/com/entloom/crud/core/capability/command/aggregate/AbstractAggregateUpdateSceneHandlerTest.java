package com.entloom.crud.core.capability.command.aggregate;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.crud.enums.RelationScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AbstractAggregateUpdateSceneHandlerTest {
    @Test
    void should_update_root_and_replace_relation_with_date_string() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(true));
        Map<String, Object> child = new LinkedHashMap<String, Object>();
        child.put("studentId", 3001L);
        child.put("classId", 3001L);
        child.put("joinedTime", "2026-04-02 08:00:00");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("groupName", "GFullU");
        payload.put("sortOrder", 2);
        payload.put("memberList", Collections.singletonList(child));

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertEquals("delegate", result);
        Assertions.assertEquals(1, delegateSpecs.size());
        WriteCommand<Map<String, Object>> writeCommand = delegateWriteCommand(delegateSpecs.get(0));
        Assertions.assertEquals(1L, writeCommand.getId());
        Assertions.assertEquals("GFullU", writeCommand.getValues().get("groupName"));
        Assertions.assertEquals(2, writeCommand.getValues().get("sortOrder"));
        Assertions.assertFalse(writeCommand.getValues().containsKey("id"));
        Assertions.assertFalse(writeCommand.getValues().containsKey("memberList"));
        Assertions.assertEquals(1, handler.syncedItems.size());
        Assertions.assertEquals(3001L, handler.syncedItems.get(0).studentId.longValue());
        Assertions.assertNotNull(handler.syncedItems.get(0).joinedTime);
    }

    @Test
    void should_touch_root_and_clear_relation_when_relation_list_is_empty() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(true));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("memberList", Collections.emptyList());

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertEquals("delegate", result);
        Assertions.assertEquals(1, delegateSpecs.size());
        WriteCommand<Map<String, Object>> writeCommand = delegateWriteCommand(delegateSpecs.get(0));
        Assertions.assertEquals(1L, writeCommand.getId());
        Assertions.assertFalse(writeCommand.getValues().containsKey("id"));
        Assertions.assertTrue(writeCommand.getValues().get("updateTime") instanceof Date);
        Assertions.assertTrue(handler.syncedItems.isEmpty());
        Assertions.assertEquals(1L, handler.syncedRootId.longValue());
    }

    @Test
    void should_not_sync_relation_when_relation_field_absent() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(true));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("groupName", "OnlyRoot");

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertEquals("delegate", result);
        Assertions.assertEquals(1, delegateSpecs.size());
        Assertions.assertNull(handler.syncedItems);
    }

    @Test
    void should_expose_only_recognized_present_fields_on_root_patch() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(true));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("groupName", "OnlyRoot");
        payload.put("memberList", Collections.emptyList());
        payload.put("unknown", "ignored");

        handler.handle(spec(payload), newDelegate(new ArrayList<CommandSpec<Object>>()));

        Assertions.assertTrue(handler.beforeRootPatch.hasField("id"));
        Assertions.assertTrue(handler.beforeRootPatch.hasField("groupName"));
        Assertions.assertTrue(handler.beforeRootPatch.hasField("memberList"));
        Assertions.assertFalse(handler.beforeRootPatch.hasField("unknown"));
        Assertions.assertFalse(handler.beforeRootPatch.getValuesForDelegate().containsKey("id"));
        Assertions.assertEquals("OnlyRoot", handler.beforeRootPatch.getValuesForDelegate().get("groupName"));
    }

    @Test
    void should_return_zero_rows_when_only_relation_present_and_root_has_no_update_time() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(false));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("memberList", Collections.emptyList());

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertTrue(result instanceof CommandResult);
        Assertions.assertTrue(delegateSpecs.isEmpty());
        Assertions.assertTrue(handler.syncedItems.isEmpty());
    }

    @Test
    void should_register_root_and_root_child_route_keys_from_relation_graph() {
        TestAggregateUpdateHandler handler = new TestAggregateUpdateHandler(registry(true));

        Assertions.assertEquals(2, handler.routeKeys().size());
    }

    private SceneDelegate<CommandSpec<Object>, Object> newDelegate(final List<CommandSpec<Object>> delegateSpecs) {
        return new SceneDelegate<CommandSpec<Object>, Object>() {
            @Override
            public Object invoke(CommandSpec<Object> delegateSpec) {
                delegateSpecs.add(delegateSpec);
                return "delegate";
            }
        };
    }

    @SuppressWarnings("unchecked")
    private WriteCommand<Map<String, Object>> delegateWriteCommand(CommandSpec<Object> delegateSpec) {
        Assertions.assertTrue(delegateSpec.getPayload() instanceof WriteCommand);
        return (WriteCommand<Map<String, Object>>) delegateSpec.getPayload();
    }

    private CommandSpec<Object> spec(Map<String, Object> payload) {
        return CommandSpec.<Object>builder()
            .op(CommandOperation.UPDATE)
            .scene("full")
            .rootType(TestGroup.class)
            .entityClasses(Arrays.<Class<?>>asList(TestGroup.class, TestMember.class))
            .payload(payload)
            .resultType(CommandResult.class)
            .build();
    }

    private EntityMetaRegistry registry(boolean includeUpdateTime) {
        final EntityMeta rootMeta = meta(TestGroup.class, "testGroup", includeUpdateTime);
        final EntityMeta childMeta = meta(TestMember.class, "testMember", true);
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(TestGroup.class);
        edge.setToEntity(TestMember.class);
        edge.setRelationField("memberList");
        edge.setFromField("id");
        edge.setToField("groupId");
        edge.setScope(RelationScope.LOCAL_DB);
        edge.setJoinKind(JoinType.LEFT);
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);
        final RelationGraph graph = RelationGraph.of(java.util.Collections.singletonList(edge));
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return entityType == TestMember.class ? childMeta : rootMeta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return getEntityMeta(entityType).getResourceDescriptor();
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return graph;
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    private EntityMeta meta(Class<?> entityType, String code, boolean includeUpdateTime) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        if (entityType == TestGroup.class) {
            fields.put("groupName", field("groupName", String.class, "group_name"));
            fields.put("sortOrder", field("sortOrder", Integer.class, "sort_order"));
            if (includeUpdateTime) {
                fields.put("updateTime", field("updateTime", Date.class, "update_time"));
            }
        } else {
            fields.put("groupId", field("groupId", Long.class, "group_id"));
            fields.put("studentId", field("studentId", Long.class, "student_id"));
            fields.put("classId", field("classId", Long.class, "class_id"));
            fields.put("joinedTime", field("joinedTime", Date.class, "joined_time"));
        }
        return new EntityMeta(
            entityType,
            new ResourceDescriptor(entityType, code, "test", Collections.<String>emptyList()),
            code,
            "id",
            null,
            fields
        );
    }

    private EntityFieldMeta field(String name, Class<?> type, String column) {
        return new EntityFieldMeta(name, type, column, true, false, true, true);
    }

    static class TestAggregateUpdateHandler extends AbstractAggregateUpdateSceneHandler<TestGroup> {
        private Long syncedRootId;
        private List<TestMember> syncedItems;
        private EntityPatch<TestGroup> beforeRootPatch;

        TestAggregateUpdateHandler(EntityMetaRegistry entityMetaRegistry) {
            super(entityMetaRegistry);
        }

        @Override
        protected AggregateUpdateSpec<TestGroup> spec() {
            return AggregateUpdateSpec.root(TestGroup.class)
                .scene("full")
                .relation("memberList", ChildSyncMode.REPLACE);
        }

        @Override
        protected void syncRelation(EntityPatch<TestGroup> rootPatch, AggregateRelationPatch<?> relationPatch) {
            syncedRootId = rootPatch.getLongId();
            syncedItems = memberItems(relationPatch);
        }

        @Override
        protected void beforeUpdate(EntityPatch<TestGroup> rootPatch, List<AggregateRelationPatch<?>> relationPatches) {
            beforeRootPatch = rootPatch;
        }

        @SuppressWarnings("unchecked")
        private List<TestMember> memberItems(AggregateRelationPatch<?> relationPatch) {
            return (List<TestMember>) (List<?>) relationPatch.getItems();
        }
    }

    public static class TestGroup {
        public Long id;
        public String groupName;
        public Integer sortOrder;
        public Date updateTime;
    }

    public static class TestMember {
        public Long id;
        public Long groupId;
        public Long studentId;
        public Long classId;
        public Date joinedTime;
    }
}
