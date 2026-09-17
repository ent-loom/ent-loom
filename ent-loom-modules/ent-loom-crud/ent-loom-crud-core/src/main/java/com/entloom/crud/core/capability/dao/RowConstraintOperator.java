package com.entloom.crud.core.capability.dao;

/**
 * 行约束比较操作符。
 */
public enum RowConstraintOperator {
    /** 等值。 */
    EQ("等于"),
    /** 集合包含。 */
    IN("集合包含"),
    /** 空值。 */
    IS_NULL("为空"),
    /** 非空值。 */
    IS_NOT_NULL("不为空"),
    /** 不等值。 */
    NE("不等于"),
    /** 大于。 */
    GT("大于"),
    /** 大于等于。 */
    GE("大于等于"),
    /** 小于。 */
    LT("小于"),
    /** 小于等于。 */
    LE("小于等于"),
    /** 模糊匹配。 */
    LIKE("模糊匹配");

    private final String displayName;

    RowConstraintOperator(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
