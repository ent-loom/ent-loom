package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudFieldAccess;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.entloom.crud.annotations.EntCrudFieldUse.ALL;
import static com.entloom.crud.annotations.EntCrudFieldUse.EXPAND;
import static com.entloom.crud.annotations.EntCrudFieldUse.FILTER;
import static com.entloom.crud.annotations.EntCrudFieldUse.RETURN;
import static org.junit.jupiter.api.Assertions.*;

class CrudFieldAccessTest {
    @Test
    void 排除全部用途并支持继承字段和部分用途() {
        AuthorizationFields fields = EntityPolicyRegistry.compileFieldLimit(meta(Root.class, "id", "secret", "cost", "name", "displayOnly"));
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
            assertFalse(fields.allows(use, "secret"), use.name());
        }
        assertFalse(fields.allows(AuthorizationFields.Use.FILTER, "cost"));
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "cost"));
        assertTrue(fields.allows(AuthorizationFields.Use.WRITE, "cost"));
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "name"));
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "displayOnly"));
        for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
            if (use != AuthorizationFields.Use.RETURN) assertFalse(fields.allows(use, "displayOnly"));
        }
    }

    @Test
    void 关联展开遵守双方字段限制且不能通过完整关系绕过() {
        EntityMeta root = meta(Root.class, "id", "secret", "cost", "name");
        EntityMeta target = meta(Target.class, "id", "publicValue", "secret", "noExpand");
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(Root.class);
        edge.setToEntity(Target.class);
        edge.setRelationField("target");
        RelationEdge hidden = new RelationEdge();
        hidden.setFromEntity(Root.class);
        hidden.setToEntity(Target.class);
        hidden.setRelationField("hiddenTarget");
        EntityMetaRegistry entities = new EntityMetaRegistry() {
            public EntityMeta getEntityMeta(Class<?> type) { return type == Root.class ? root : type == Target.class ? target : null; }
            public ResourceDescriptor getResourceDescriptor(Class<?> type) { return getEntityMeta(type).getResourceDescriptor(); }
            public Collection<EntityMeta> getEntityMetas() { return Arrays.asList(root, target); }
            public RelationGraph getRelationGraph(Class<?> type) { return RelationGraph.of(Arrays.asList(edge, hidden)); }
            public void validateOrThrow() { }
        };
        AuthorizationFields fields = EntityPolicyRegistry.compileFieldLimit(root, entities);
        assertTrue(fields.allows(AuthorizationFields.Use.EXPAND, "target.publicValue"));
        assertFalse(fields.allows(AuthorizationFields.Use.EXPAND, "target.secret"));
        assertFalse(fields.allows(AuthorizationFields.Use.EXPAND, "target.noExpand"));
        assertFalse(fields.allows(AuthorizationFields.Use.EXPAND, "target"));
        assertFalse(fields.allows(AuthorizationFields.Use.EXPAND, "hiddenTarget.publicValue"));
        assertFalse(fields.allows(AuthorizationFields.Use.EXPAND, "hiddenTarget"));
    }

    @Test
    void 排除用途必须非空且不能重复或混用全部用途() {
        for (Class<?> type : Arrays.asList(Empty.class, Duplicate.class, Mixed.class, Both.class)) {
            assertThrows(ValidationException.class, () -> EntityPolicyRegistry.compileFieldLimit(meta(type, "value")));
        }
    }

    private static EntityMeta meta(Class<?> type, String... names) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        for (String name : names) fields.put(name, new EntityFieldMeta(name, String.class, name, false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, type.getSimpleName(), null, Collections.emptyList()),
            "test", "id", null, fields);
    }

    static class Base { @EntCrudFieldAccess(exclude = ALL) String secret; }
    static class Root extends Base {
        String id;
        @EntCrudFieldAccess(exclude = FILTER) String cost;
        String name;
        @EntCrudFieldAccess(include = RETURN) String displayOnly;
        Target target;
        @EntCrudFieldAccess(exclude = ALL) Target hiddenTarget;
    }
    static class Target {
        String id;
        String publicValue;
        @EntCrudFieldAccess(exclude = ALL) String secret;
        @EntCrudFieldAccess(exclude = EXPAND) String noExpand;
    }
    static class Empty { @EntCrudFieldAccess(exclude = {}) String value; }
    static class Duplicate { @EntCrudFieldAccess(exclude = {RETURN, RETURN}) String value; }
    static class Mixed { @EntCrudFieldAccess(exclude = {ALL, RETURN}) String value; }
    static class Both { @EntCrudFieldAccess(include = RETURN, exclude = FILTER) String value; }
}
