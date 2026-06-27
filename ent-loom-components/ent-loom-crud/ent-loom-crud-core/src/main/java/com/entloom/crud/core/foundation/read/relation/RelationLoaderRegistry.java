package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * RelationLoader 注册表。
 */
public class RelationLoaderRegistry {
    private final List<RelationLoader> loaders;

    public RelationLoaderRegistry() {
        this(null);
    }

    public RelationLoaderRegistry(Collection<? extends RelationLoader> loaders) {
        this.loaders = new ArrayList<RelationLoader>();
        if (loaders == null) {
            return;
        }
        for (RelationLoader loader : loaders) {
            if (loader != null) {
                this.loaders.add(loader);
            }
        }
    }

    public static RelationLoaderRegistry empty() {
        return new RelationLoaderRegistry();
    }

    public RelationLoader resolve(RelationEdge edge) {
        for (RelationLoader loader : loaders) {
            if (loader.supports(edge)) {
                return loader;
            }
        }
        return null;
    }

    public boolean hasLoader(RelationEdge edge) {
        return resolve(edge) != null;
    }

    public List<RelationLoader> getLoaders() {
        return new ArrayList<RelationLoader>(loaders);
    }
}
