package com.entloom.ddl.enums;

/**
 * 值生成策略。
 */
public enum GenerationStrategy {
    /** 未声明，使用项目默认或 Meta 映射。 */
    UNSET,
    /** 不使用数据库生成，显式覆盖默认策略。 */
    NONE,
    /** 数据库自增。 */
    AUTO_INCREMENT,
    /** 数据库标识列。 */
    IDENTITY,
    /** 数据库序列。 */
    SEQUENCE,
    /** UUID 生成。 */
    UUID
}
