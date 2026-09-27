package com.entloom.meta.enums;

/** 主键值的提供方；具体生成机制由适配器映射。 */
public enum EntIdPolicy {
    /** 未声明，使用项目默认或模块约定。 */
    UNSET,
    /** 由数据库生成。 */
    DATABASE,
    /** 由应用生成。 */
    APPLICATION,
    /** 由调用方提供。 */
    ASSIGNED
}
