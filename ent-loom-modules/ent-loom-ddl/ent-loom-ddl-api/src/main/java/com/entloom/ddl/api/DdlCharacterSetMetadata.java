package com.entloom.ddl.api;

/**
 * 目标存储字符集事实；最大编码宽度来自数据库 CHARACTER_SETS.MAXLEN。
 * 未知值不能作为自动容量推导的依据，非文本列也使用未知值。
 */
public final class DdlCharacterSetMetadata {
    private final String characterSet;
    private final String collation;
    private final int maxBytesPerCharacter;

    public DdlCharacterSetMetadata(String characterSet, String collation, int maxBytesPerCharacter) {
        this.characterSet = characterSet == null ? "" : characterSet.trim();
        this.collation = collation == null ? "" : collation.trim();
        this.maxBytesPerCharacter = maxBytesPerCharacter;
        if (this.characterSet.isEmpty() && this.collation.isEmpty() && maxBytesPerCharacter == 0) {
            return;
        }
        if (!this.characterSet.matches("[A-Za-z0-9_]+") || !this.collation.matches("[A-Za-z0-9_]+")
                || maxBytesPerCharacter <= 0) {
            throw new IllegalArgumentException("字符集、排序规则及最大编码宽度必须完整且合法");
        }
    }

    public static DdlCharacterSetMetadata unknown() {
        return new DdlCharacterSetMetadata("", "", 0);
    }

    public boolean known() {
        return maxBytesPerCharacter > 0;
    }

    public String characterSet() {
        return characterSet;
    }

    public String collation() {
        return collation;
    }

    public int maxBytesPerCharacter() {
        return maxBytesPerCharacter;
    }
}
