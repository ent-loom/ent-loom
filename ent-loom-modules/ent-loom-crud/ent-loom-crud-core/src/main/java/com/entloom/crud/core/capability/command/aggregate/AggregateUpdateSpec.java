package com.entloom.crud.core.capability.command.aggregate;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.util.RouteKeyFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 聚合 UPDATE 场景配置。
 *
 * @param <R> 聚合根实体类型
 */
public class AggregateUpdateSpec<R> {
    private final Class<R> rootType;
    private String scene;
    private final List<AggregateRelationSpec> relationSpecs = new ArrayList<AggregateRelationSpec>();

    private AggregateUpdateSpec(Class<R> rootType) {
        this.rootType = rootType;
        if (rootType == null) {
            throw new IllegalArgumentException("rootType 不能为空");
        }
    }

    public static <R> AggregateUpdateSpec<R> root(Class<R> rootType) {
        return new AggregateUpdateSpec<R>(rootType);
    }

    public AggregateUpdateSpec<R> scene(String scene) {
        this.scene = scene;
        return this;
    }

    public AggregateUpdateSpec<R> relation(String relationField, ChildSyncMode syncMode) {
        relationSpecs.add(new AggregateRelationSpec(relationField, syncMode));
        return this;
    }

    public AggregateUpdateSpec<R> replaceChildren(String relationField) {
        return relation(relationField, ChildSyncMode.REPLACE);
    }

    public Class<R> getRootType() {
        return rootType;
    }

    public String getScene() {
        return scene;
    }

    public List<AggregateRelationSpec> getRelationSpecs() {
        return Collections.unmodifiableList(relationSpecs);
    }

    public Set<CrudRouteKey> routeKeys(List<RelationEdge> relationEdges) {
        validate();
        String normalizedScene = RouteKeyFactory.normalizeScene(scene);
        Set<CrudRouteKey> keys = new LinkedHashSet<CrudRouteKey>();
        keys.add(new CrudRouteKey(
            Collections.singletonList(rootType.getName()),
            CrudOperationKey.of(CommandOperation.UPDATE),
            normalizedScene
        ));
        if (relationEdges != null) {
            for (RelationEdge edge : relationEdges) {
                if (edge == null || edge.getToEntity() == null) {
                    continue;
                }
                List<String> entityNames = new ArrayList<String>();
                entityNames.add(rootType.getName());
                entityNames.add(edge.getToEntity().getName());
                keys.add(new CrudRouteKey(entityNames, CrudOperationKey.of(CommandOperation.UPDATE), normalizedScene));
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    private void validate() {
        if (scene == null || scene.trim().isEmpty()) {
            throw new IllegalArgumentException("scene 不能为空");
        }
    }
}
