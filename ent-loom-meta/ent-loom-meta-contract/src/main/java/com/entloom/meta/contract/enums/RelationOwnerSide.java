package com.entloom.meta.contract.enums;

/**
 * 关系执行数据的拥有侧。
 */
public enum RelationOwnerSide {
    /** 声明关系的实体持有关系字段。 */
    DECLARING_ENTITY,
    /** 目标实体持有反向关系字段。 */
    TARGET_ENTITY,
    /** 关系由中间表持有。 */
    JOIN_TABLE,
    /** 暂未能从 P0 元信息确定拥有侧。 */
    UNKNOWN
}
