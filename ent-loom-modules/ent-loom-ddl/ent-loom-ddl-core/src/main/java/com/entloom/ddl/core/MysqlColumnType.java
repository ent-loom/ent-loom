package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlCharacterSetMetadata;

/** 一次解析后的目标类型；比较用基础类型，DDL 使用带字符集的物理定义。 */
public final class MysqlColumnType {
    private final String sqlType;
    private final String definition;
    private final boolean textFamily;
    private final String warning;

    MysqlColumnType(String sqlType, DdlCharacterSetMetadata characterSet, boolean textual,
                    boolean textFamily, String warning) {
        this.sqlType = sqlType;
        this.definition = sqlType + (textual && characterSet.known()
                ? " CHARACTER SET " + characterSet.characterSet() + " COLLATE " + characterSet.collation() : "");
        this.textFamily = textFamily;
        this.warning = warning;
    }

    public String sqlType() { return sqlType; }
    public String definition() { return definition; }
    public boolean textFamily() { return textFamily; }
    public String warning() { return warning; }
}
