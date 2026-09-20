package com.entloom.crud.api.enums;

/**
 * 实体分页总数策略。
 */
public enum CountMode {
    /** 不查询总数，只通过多取一条判断是否存在下一页。 */
    NONE,
    /** 查询精确总数。 */
    ALWAYS
}
