package com.entloom.crud.api.access;

import java.util.Objects;

/** 简单身份的不可变定义；声明结构及转换规则，不代表认证或归属事实。 */
public final class BusinessIdentityDefinition {
    private final String code;
    private final String idField;
    private final BusinessIdentityIdType idType;

    public BusinessIdentityDefinition(String code, String idField, BusinessIdentityIdType idType) {
        this.code = text(code, "身份码");
        this.idField = text(idField, "身份 ID 字段");
        if (!idField.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("身份 ID 字段必须是规范属性名: " + idField);
        }
        this.idType = Objects.requireNonNull(idType, "身份 ID 类型不能为空");
    }

    public String getCode() { return code; }
    public String getIdField() { return idField; }
    public BusinessIdentityIdType getIdType() { return idType; }

    /** 只转换并校验 ID，不验证调用主体，不能直接作为准入依据。 */
    public ConfiguredBusinessIdentity snapshot(String id) {
        return new ConfiguredBusinessIdentity(this, idType.parse(text(id, "身份 ID")));
    }

    static String text(String value, String label) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + "不能为空或包含首尾空格");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BusinessIdentityDefinition)) return false;
        BusinessIdentityDefinition that = (BusinessIdentityDefinition) other;
        return code.equals(that.code) && idField.equals(that.idField) && idType == that.idType;
    }

    @Override
    public int hashCode() { return Objects.hash(code, idField, idType); }
}
