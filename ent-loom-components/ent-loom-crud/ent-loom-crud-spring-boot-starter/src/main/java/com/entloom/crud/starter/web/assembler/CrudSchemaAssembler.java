package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.model.CrudFieldSchema;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudSchema;
import com.entloom.crud.api.model.CrudStatsData;
import com.entloom.crud.api.model.CrudStatsSchema;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 响应 schema 组装器。
 */
public class CrudSchemaAssembler {
    private final EntityMetaRegistry entityMetaRegistry;

    public CrudSchemaAssembler(EntityMetaRegistry entityMetaRegistry) {
        this.entityMetaRegistry = entityMetaRegistry;
    }

    public Map<String, Object> queryMeta(QuerySpec<?> spec) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("schema", buildQuerySchema(spec));
        return meta;
    }

    public Map<String, Object> statsMeta(BaseSpec spec, CrudStatsData data) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<String, Object>();
        CrudStatsSchema schema = new CrudStatsSchema();
        schema.setEntity(resolveEntityName(spec == null ? null : spec.getRootType()));
        schema.setMode(data == null ? null : data.getMode());
        if (data != null && data.getColumns() != null) {
            schema.getDimensions().addAll(data.getColumns().getDimensions());
            schema.getMetrics().addAll(data.getColumns().getMetrics());
        }
        if (data != null && data.getMetrics() != null && schema.getMetrics().isEmpty()) {
            schema.getMetrics().addAll(data.getMetrics().keys());
        }
        meta.put("schema", schema);
        return meta;
    }

    private CrudSchema buildQuerySchema(QuerySpec<?> spec) {
        Class<?> rootType = spec == null ? null : spec.getRootType();
        Class<?> resultType = spec == null ? null : spec.getResultType();
        CrudSchema schema = new CrudSchema();
        schema.setEntity(resolveEntityName(rootType));
        schema.setViewType(resultType == null ? null : resultType.getName());
        if (resultType == null || CrudRecord.class.isAssignableFrom(resultType) || Map.class.isAssignableFrom(resultType)) {
            schema.setKind("record");
            populateRecordFields(schema, rootType);
            return schema;
        }
        EntityMeta entityMeta = lookupMeta(resultType);
        if (entityMeta != null) {
            schema.setKind("entity");
            populateEntityFields(schema, entityMeta, true);
            return schema;
        }
        schema.setKind("view");
        populateViewFields(schema, resultType);
        return schema;
    }

    private void populateRecordFields(CrudSchema schema, Class<?> rootType) {
        EntityMeta entityMeta = lookupMeta(rootType);
        if (entityMeta == null) {
            return;
        }
        for (EntityFieldMeta fieldMeta : entityMeta.getFieldMetas().values()) {
            CrudFieldSchema field = new CrudFieldSchema();
            field.setName(fieldMeta.getColumnName());
            field.setSourceField(fieldMeta.getFieldName());
            field.setColumn(fieldMeta.getColumnName());
            field.setJavaType(fieldMeta.getJavaType().getName());
            field.setNullable(fieldMeta.isNullable());
            field.setRelation(fieldMeta.isRelation());
            field.setFilterable(fieldMeta.isFilterable());
            field.setSortable(fieldMeta.isSortable());
            schema.getFields().add(field);
        }
    }

    private void populateEntityFields(CrudSchema schema, EntityMeta entityMeta, boolean useFieldNameAsResponseName) {
        for (EntityFieldMeta fieldMeta : entityMeta.getFieldMetas().values()) {
            CrudFieldSchema field = new CrudFieldSchema();
            field.setName(useFieldNameAsResponseName ? fieldMeta.getFieldName() : fieldMeta.getColumnName());
            field.setSourceField(fieldMeta.getFieldName());
            field.setColumn(fieldMeta.getColumnName());
            field.setJavaType(fieldMeta.getJavaType().getName());
            field.setNullable(fieldMeta.isNullable());
            field.setRelation(fieldMeta.isRelation());
            field.setFilterable(fieldMeta.isFilterable());
            field.setSortable(fieldMeta.isSortable());
            schema.getFields().add(field);
        }
    }

    private void populateViewFields(CrudSchema schema, Class<?> viewType) {
        Class<?> current = viewType;
        while (current != null && current != Object.class) {
            for (Field declaredField : current.getDeclaredFields()) {
                if (Modifier.isStatic(declaredField.getModifiers())) {
                    continue;
                }
                CrudFieldSchema field = new CrudFieldSchema();
                field.setName(declaredField.getName());
                field.setSourceField(declaredField.getName());
                field.setJavaType(declaredField.getType().getName());
                field.setNullable(!declaredField.getType().isPrimitive());
                field.setRelation(false);
                field.setFilterable(false);
                field.setSortable(false);
                schema.getFields().add(field);
            }
            current = current.getSuperclass();
        }
    }

    private EntityMeta lookupMeta(Class<?> entityType) {
        if (entityType == null || entityMetaRegistry == null) {
            return null;
        }
        try {
            return entityMetaRegistry.getEntityMeta(entityType);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveEntityName(Class<?> rootType) {
        EntityMeta meta = lookupMeta(rootType);
        if (meta != null) {
            return meta.getResourceDescriptor().getResourceCode();
        }
        return rootType == null ? null : rootType.getSimpleName();
    }
}
