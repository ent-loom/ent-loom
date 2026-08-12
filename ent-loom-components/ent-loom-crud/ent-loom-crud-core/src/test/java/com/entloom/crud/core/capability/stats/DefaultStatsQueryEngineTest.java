package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultStatsQueryEngineTest {
    @Test
    void should_dispatch_empty_scene_stats_handler_before_default_executor() {
        final AtomicReference<StatsSpec> executedSpecRef = new AtomicReference<StatsSpec>();
        EntityMetaRegistry metaRegistry = testMetaRegistry();
        StatsQueryExecutor executor = new StatsQueryExecutor() {
            @Override
            public StatsResult execute(StatsSpec spec, EntityMeta rootMeta) {
                executedSpecRef.set(spec);
                StatsResult result = new StatsResult();
                result.setMode(spec.getMode());
                return result;
            }
        };
        DefaultSceneHandlerRegistry<StatsSpec, StatsResult> registry =
            new DefaultSceneHandlerRegistry<StatsSpec, StatsResult>();
        registry.register(new EmptySceneStatsHandler());
        DefaultStatsQueryEngine engine = new DefaultStatsQueryEngine(metaRegistry, executor, registry);

        StatsSpec spec = StatsSpec.builder()
            .rootType(Object.class)
            .payload(new StatsQueryPayload())
            .build();

        StatsResult result = engine.stats(spec);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(executedSpecRef.get());
        Assertions.assertEquals(Boolean.TRUE, executedSpecRef.get().getAttributes().get("emptySceneHandled"));
    }

    private static class EmptySceneStatsHandler implements StatsSceneHandler {
        @Override
        public Set<CrudRouteKey> routeKeys() {
            return Collections.singleton(new CrudRouteKey(
                Collections.singletonList(Object.class.getName()),
                CrudOperationKey.of(StatsOperation.QUERY),
                RouteKeyFactory.normalizeScene(null)
            ));
        }

        @Override
        public StatsResult handle(StatsSpec spec, SceneDelegate<StatsSpec, StatsResult> delegate) {
            Map<String, Object> attributes = new HashMap<String, Object>(spec.getAttributes());
            attributes.put("emptySceneHandled", Boolean.TRUE);
            return delegate.invoke(spec.toBuilder().attributes(attributes).build());
        }
    }

    private EntityMetaRegistry testMetaRegistry() {
        final ResourceDescriptor descriptor = new ResourceDescriptor(
            Object.class,
            "Object",
            "test-service",
            Collections.<String>emptyList()
        );
        final EntityMeta meta = new EntityMeta(
            Object.class,
            descriptor,
            "t_object",
            "id",
            null,
            Collections.emptyMap()
        );
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return meta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return descriptor;
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return RelationGraph.empty();
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }
}
