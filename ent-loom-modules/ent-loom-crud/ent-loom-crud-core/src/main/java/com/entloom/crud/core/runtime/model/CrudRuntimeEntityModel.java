package com.entloom.crud.core.runtime.model;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * CRUD 运行期实体元数据。
 */
@Getter
public final class CrudRuntimeEntityModel {
    private final Class<?> entityType;
    private final ResourceDescriptor resourceDescriptor;
    private final String table;
    private final CrudRuntimeIdentityModel identity;
    private final String logicDeleteField;
    private final String ownerService;
    private final Map<String, CrudRuntimeFieldModel> fields;
    private final CrudRuntimeCapabilityModel capabilities;

    public CrudRuntimeEntityModel(
        Class<?> entityType,
        ResourceDescriptor resourceDescriptor,
        String table,
        CrudRuntimeIdentityModel identity,
        String logicDeleteField,
        Map<String, CrudRuntimeFieldModel> fields,
        CrudRuntimeCapabilityModel capabilities
    ) {
        this.entityType = entityType;
        this.resourceDescriptor = resourceDescriptor;
        this.table = table;
        this.identity = identity;
        this.logicDeleteField = logicDeleteField;
        this.ownerService = resourceDescriptor == null ? null : resourceDescriptor.getOwnerService();
        this.fields = fields == null
            ? Collections.<String, CrudRuntimeFieldModel>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, CrudRuntimeFieldModel>(fields));
        this.capabilities = capabilities == null ? CrudRuntimeCapabilityModel.empty() : capabilities;
    }

    public static CrudRuntimeEntityModel from(EntityMeta meta) {
        if (meta == null) {
            return null;
        }
        LinkedHashMap<String, CrudRuntimeFieldModel> fields = new LinkedHashMap<String, CrudRuntimeFieldModel>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            fields.put(entry.getKey(), CrudRuntimeFieldModel.from(entry.getValue()));
        }
        return new CrudRuntimeEntityModel(
            meta.getEntityType(),
            meta.getResourceDescriptor(),
            meta.getTable(),
            CrudRuntimeIdentityModel.from(meta),
            meta.getLogicDeleteField(),
            fields,
            CrudRuntimeCapabilityModel.empty()
        );
    }

    public CrudRuntimeFieldModel getField(String fieldName) {
        return fields.get(fieldName);
    }

    public EntityMeta toEntityMeta() {
        LinkedHashMap<String, EntityFieldMeta> fieldMetas = new LinkedHashMap<String, EntityFieldMeta>();
        for (Map.Entry<String, CrudRuntimeFieldModel> entry : fields.entrySet()) {
            CrudRuntimeFieldModel field = entry.getValue();
            if (field == null) {
                continue;
            }
            fieldMetas.put(
                entry.getKey(),
                new EntityFieldMeta(
                    field.getFieldName(),
                    field.getJavaType(),
                    field.getColumnName(),
                    field.isNullable(),
                    field.isRelation(),
                    field.isFilterable(),
                    field.isSortable(),
                    field.isWritable(),
                    field.isScopeField(),
                    field.isImmutable(),
                    field.getExportable(),
                    field.getExportDefaultVisible(),
                    field.getExportLabel(),
                    field.getExportFormat(),
                    field.getDictionaryCode(),
                    field.getDisplayField()
                )
            );
        }
        return new EntityMeta(
            entityType,
            resourceDescriptor,
            table,
            identity == null ? null : identity.getIdField(),
            identity == null ? EntityIdPolicy.EXPLICIT : identity.getIdPolicy(),
            logicDeleteField,
            fieldMetas
        );
    }
}
