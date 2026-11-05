package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessIdentityIdType;
import lombok.Data;

/** 可信业务身份的标识结构；不描述实体字段、关系或访问权限。 */
@Data
public class CrudIdentityProperties {
    /** 身份快照属性名，缺省为身份码 + Id；与实体字段无关。 */
    private String idField;
    /** 业务标识的显式标量类型。 */
    private BusinessIdentityIdType idType;
}
