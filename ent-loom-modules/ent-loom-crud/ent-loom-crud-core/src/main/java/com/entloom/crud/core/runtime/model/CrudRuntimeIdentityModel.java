package com.entloom.crud.core.runtime.model;

import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import lombok.Getter;

/**
 * CRUD 运行期主键元数据。
 */
@Getter
public final class CrudRuntimeIdentityModel {
    private final String idField;
    private final String idColumn;
    private final Class<?> idType;
    private final EntityIdPolicy idPolicy;

    public CrudRuntimeIdentityModel(String idField, String idColumn, Class<?> idType, EntityIdPolicy idPolicy) {
        this.idField = idField;
        this.idColumn = idColumn;
        this.idType = idType == null ? Object.class : idType;
        this.idPolicy = idPolicy == null ? EntityIdPolicy.EXPLICIT : idPolicy;
    }

    public static CrudRuntimeIdentityModel from(EntityMeta meta) {
        if (meta == null) {
            return null;
        }
        String idField = meta.getIdField();
        CrudRuntimeFieldModel field = CrudRuntimeFieldModel.from(meta.resolveFieldMeta(idField));
        return new CrudRuntimeIdentityModel(
            idField,
            meta.resolveColumn(idField),
            field == null ? Object.class : field.getJavaType(),
            meta.getIdPolicy()
        );
    }

    public boolean isComposite() {
        return idPolicy == EntityIdPolicy.COMPOSITE || (idField != null && idField.indexOf(',') >= 0);
    }
}
