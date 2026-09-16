package com.entloom.crud.api.enums;

/**
 * CRUD 实体主键写入策略。
 */
public enum CrudIdPolicy {
    /** 未显式配置，由解析器按常见持久化注解推断。 */
    UNSET,
    /** 由调用方显式提供主键。 */
    EXPLICIT,
    /** 由数据库生成主键。 */
    GENERATED,
    /** 由应用层生成主键。 */
    APPLICATION,
    /** 联合主键，默认 JDBC 写入链暂不支持。 */
    COMPOSITE
}
