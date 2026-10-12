package com.entloom.crud.api.access;

/** 简单业务身份 ID 的标量类型。 */
public enum BusinessIdentityIdType {
    /** 文本标识。 */
    STRING(String.class),
    /** 正整数长标识。 */
    LONG(Long.class),
    /** 正整数标识。 */
    INTEGER(Integer.class),
    /** UUID 标识。 */
    UUID(java.util.UUID.class);

    private final Class<?> javaType;

    BusinessIdentityIdType(Class<?> javaType) { this.javaType = javaType; }

    public Class<?> getJavaType() { return javaType; }

    Object parse(String id) {
        Object value;
        switch (this) {
            case LONG: value = Long.valueOf(id); break;
            case INTEGER: value = Integer.valueOf(id); break;
            case UUID: value = java.util.UUID.fromString(id); break;
            case STRING: value = id; break;
            default: throw new IllegalStateException("未知身份 ID 类型: " + this);
        }
        if (value instanceof Number && ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException("数值身份 ID 必须为正数");
        }
        if (!id.equals(value.toString())) {
            throw new IllegalArgumentException("身份 ID 必须使用规范文本");
        }
        return value;
    }
}
