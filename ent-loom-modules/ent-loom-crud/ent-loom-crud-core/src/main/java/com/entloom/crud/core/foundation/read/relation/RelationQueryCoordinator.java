package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 关系查询编排器。
 */
public class RelationQueryCoordinator {
    private final PathResolver pathResolver;
    private final RelationQueryValidator validator;

    public RelationQueryCoordinator() {
        this((EntityMetaRegistry) null, (PathResolver) null, (RelationQueryValidator) null);
    }

    public RelationQueryCoordinator(EntityMetaRegistry metaRegistry) {
        this(metaRegistry, (PathResolver) null, (RelationQueryValidator) null);
    }

    public RelationQueryCoordinator(
        EntityMetaRegistry metaRegistry,
        RelationQueryPolicy policy,
        RelationLoaderRegistry loaderRegistry
    ) {
        this(
            metaRegistry,
            null,
            new RelationQueryValidator(policy, loaderRegistry)
        );
    }

    public RelationQueryCoordinator(PathResolver pathResolver, RelationQueryValidator validator) {
        this(null, pathResolver, validator);
    }

    public RelationQueryCoordinator(
        EntityMetaRegistry metaRegistry,
        PathResolver pathResolver,
        RelationQueryValidator validator
    ) {
        this.pathResolver = pathResolver == null ? new PathResolver(metaRegistry) : pathResolver;
        this.validator = validator == null ? new RelationQueryValidator() : validator;
    }

    public RelationQueryModel resolve(QuerySpec<?> spec, RelationGraph relationGraph) {
        RelationQueryModel model = pathResolver.resolve(spec, relationGraph);
        validator.validate(spec, model);
        return model;
    }
}
