package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.annotations.EntCrudFieldAccess;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.entloom.crud.annotations.EntCrudFieldUse.ALL;
import static org.junit.jupiter.api.Assertions.*;

class JdbcFieldAccessRelationTest {
    @Test
    void 完整关系授权也不能返回禁止展开的字段() {
        EntityMeta root = meta(Root.class, "id");
        EntityMeta target = meta(Target.class, "id", "name", "secret");
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(Root.class);
        edge.setToEntity(Target.class);
        edge.setRelationField("target");
        edge.setFromField("id");
        edge.setToField("id");
        edge.setScope(RelationScope.LOCAL_DB);
        edge.setCardinality(RelationCardinality.MANY_TO_ONE);
        EntityMetaRegistry registry = new EntityMetaRegistry() {
            public EntityMeta getEntityMeta(Class<?> type) { return type == Root.class ? root : type == Target.class ? target : null; }
            public ResourceDescriptor getResourceDescriptor(Class<?> type) { return getEntityMeta(type).getResourceDescriptor(); }
            public Collection<EntityMeta> getEntityMetas() { return Arrays.asList(root, target); }
            public RelationGraph getRelationGraph(Class<?> type) { return RelationGraph.of(Collections.singletonList(edge)); }
            public void validateOrThrow() { }
        };
        JdbcAuthorizedRelationReader reader = new JdbcAuthorizedRelationReader(registry, null, null, null);
        AuthorizationFields wildcard = AuthorizationFields.of(Collections.singletonMap(AuthorizationFields.Use.EXPAND,
            Collections.singletonList("target")));
        assertEquals(Arrays.asList("id", "name"), reader.prepare(root, Collections.singletonList("target"), wildcard).get(0).returned);
        AuthorizationFields onlySecret = AuthorizationFields.of(Collections.singletonMap(AuthorizationFields.Use.EXPAND,
            Collections.singletonList("target.secret")));
        assertThrows(PermissionDeniedException.class, () -> reader.prepare(root, Collections.singletonList("target"), onlySecret));
    }

    private static EntityMeta meta(Class<?> type, String... names) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        for (String name : names) fields.put(name, new EntityFieldMeta(name, String.class, name, false, false, true, true));
        return new EntityMeta(type, new ResourceDescriptor(type, type.getSimpleName(), null, Collections.emptyList()),
            "test", "id", null, fields);
    }

    static class Root { String id; Target target; }
    static class Target { String id; String name; @EntCrudFieldAccess(exclude = ALL) String secret; }
}
