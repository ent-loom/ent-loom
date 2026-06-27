package com.entloom.crud.enums;

/**
 * 查询策略枚举。
 */
public enum QueryStrategy {
    /** 默认策略（未显式指定）。 */
    DEFAULT,
    /** 主表优先分页，再批量补数。 */
    ROOT_FIRST,
//    /** EXISTS 子查询策略。 */
//    EXISTS,
//    /** JOIN 直连策略。 */
//    JOIN
}
