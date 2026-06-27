package com.entloom.meta.enums;

/**
 * 注解层关系基数。
 */
public enum RelationCardinality {
    /** 一对一。 */
    ONE_TO_ONE,
    /** 一对多。 */
    ONE_TO_MANY,
    /** 多对一。 */
    MANY_TO_ONE,
    /** 多对多。 */
    MANY_TO_MANY
}
