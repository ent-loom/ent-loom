package com.entloom.crud.core.runtime.model;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * CRUD 运行期统一元数据模型。
 */
@Getter
public final class CrudRuntimeModel {
    private final Map<Class<?>, CrudRuntimeEntityModel> entities;
    private final Map<String, Class<?>> resourceTypes;
    private final List<CrudRuntimeRelationModel> relations;

    public CrudRuntimeModel(
        Collection<CrudRuntimeEntityModel> entities,
        Collection<CrudRuntimeRelationModel> relations
    ) {
        this.entities = indexEntities(entities);
        this.resourceTypes = indexResourceTypes(this.entities.values());
        this.relations = relations == null
            ? Collections.<CrudRuntimeRelationModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<CrudRuntimeRelationModel>(relations));
    }

    public static CrudRuntimeModel from(Collection<EntityMeta> entityMetas, Collection<RelationEdge> relationEdges) {
        List<CrudRuntimeEntityModel> entities = new ArrayList<CrudRuntimeEntityModel>();
        if (entityMetas != null) {
            for (EntityMeta entityMeta : entityMetas) {
                CrudRuntimeEntityModel model = CrudRuntimeEntityModel.from(entityMeta);
                if (model != null) {
                    entities.add(model);
                }
            }
        }
        List<CrudRuntimeRelationModel> relations = new ArrayList<CrudRuntimeRelationModel>();
        if (relationEdges != null) {
            for (RelationEdge relationEdge : relationEdges) {
                CrudRuntimeRelationModel model = CrudRuntimeRelationModel.from(relationEdge);
                if (model != null) {
                    relations.add(model);
                }
            }
        }
        return new CrudRuntimeModel(entities, relations);
    }

    public CrudRuntimeEntityModel getEntity(Class<?> entityType) {
        return entities.get(entityType);
    }

    public List<RelationEdge> relationEdges() {
        List<RelationEdge> edges = new ArrayList<RelationEdge>();
        for (CrudRuntimeRelationModel relation : relations) {
            edges.add(relation.toRelationEdge());
        }
        return Collections.unmodifiableList(edges);
    }

    public Class<?> resolveEntityType(String resourceCode) {
        if (resourceCode == null) {
            return null;
        }
        String normalized = resourceCode.trim();
        Class<?> direct = resourceTypes.get(normalized);
        if (direct != null) {
            return direct;
        }
        return resourceTypes.get(normalized.toLowerCase(Locale.ROOT));
    }

    private Map<Class<?>, CrudRuntimeEntityModel> indexEntities(Collection<CrudRuntimeEntityModel> source) {
        LinkedHashMap<Class<?>, CrudRuntimeEntityModel> result = new LinkedHashMap<Class<?>, CrudRuntimeEntityModel>();
        if (source == null) {
            return Collections.unmodifiableMap(result);
        }
        for (CrudRuntimeEntityModel entity : source) {
            if (entity == null || entity.getEntityType() == null) {
                continue;
            }
            CrudRuntimeEntityModel previous = result.putIfAbsent(entity.getEntityType(), entity);
            if (previous != null && previous != entity) {
                throw new ValidationException("实体运行时模型重复: " + entity.getEntityType().getName());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Class<?>> indexResourceTypes(Collection<CrudRuntimeEntityModel> entities) {
        LinkedHashMap<String, Class<?>> result = new LinkedHashMap<String, Class<?>>();
        if (entities == null) {
            return Collections.unmodifiableMap(result);
        }
        for (CrudRuntimeEntityModel entity : entities) {
            ResourceDescriptor descriptor = entity.getResourceDescriptor();
            if (descriptor == null) {
                continue;
            }
            indexResourceCode(result, descriptor.getResourceCode(), entity.getEntityType());
            for (String alias : descriptor.getAliases()) {
                indexResourceCode(result, alias, entity.getEntityType());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void indexResourceCode(Map<String, Class<?>> result, String code, Class<?> entityType) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        indexResourceCodeVariant(result, code.trim(), entityType);
        indexResourceCodeVariant(result, code.trim().toLowerCase(Locale.ROOT), entityType);
    }

    private void indexResourceCodeVariant(Map<String, Class<?>> result, String code, Class<?> entityType) {
        Class<?> previous = result.putIfAbsent(code, entityType);
        if (previous != null && !previous.equals(entityType)) {
            throw new ValidationException("资源编码或别名重复: " + code);
        }
    }
}
