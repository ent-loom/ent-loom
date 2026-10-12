package com.entloom.crud.api.access;

import java.util.Objects;

/** 简单业务身份的固定结构快照；只保存已转换的不可变标量，不暴露任意属性 Map。 */
public final class ConfiguredBusinessIdentity implements BusinessIdentity {
    private final BusinessIdentityDefinition definition;
    private final Object idValue;

    ConfiguredBusinessIdentity(BusinessIdentityDefinition definition, Object idValue) {
        this.definition = definition;
        this.idValue = idValue;
    }

    public BusinessIdentityDefinition getDefinition() { return definition; }
    public Object getIdValue() { return idValue; }

    public Object read(String field) {
        if (!definition.getIdField().equals(field)) {
            throw new IllegalArgumentException("身份没有声明该属性: " + getIdentityCode() + "/" + field);
        }
        return idValue;
    }

    @Override
    public String getIdentityCode() { return definition.getCode(); }

    @Override
    public String getIdentityId() { return idValue.toString(); }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfiguredBusinessIdentity)) return false;
        ConfiguredBusinessIdentity that = (ConfiguredBusinessIdentity) other;
        return definition.equals(that.definition) && idValue.equals(that.idValue);
    }

    @Override
    public int hashCode() { return Objects.hash(definition, idValue); }
}
