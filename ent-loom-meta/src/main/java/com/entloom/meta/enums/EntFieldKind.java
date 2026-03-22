package com.entloom.meta.enums;

/**
 * 属性字段的基础数据类型分类。
 */
public enum EntFieldKind {
    /** 主键类型。 */
    ID,
    /** 外部引用 ID。 */
    REF_ID,
    /** 普通文本。 */
    TEXT,
    /** 富文本内容。 */
    RICH_CONTENT,
    /** 数值。 */
    NUMBER,
    /** 枚举值。 */
    ENUM,
    /** 布尔标记。 */
    FLAG,
    /** 日期时间。 */
    DATETIME,
    /** 媒体资源。 */
    MEDIA,
    /** JSON 文档。 */
    JSON_DOC,
}
