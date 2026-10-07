package com.entloom.crud.core.governance.identity;

import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.core.exception.ValidationException;
import java.lang.reflect.Modifier;

/** 项目身份 code、具体快照类型与可信解析器的不可变注册声明。 */
public final class BusinessIdentityRegistration<I extends BusinessIdentity> {
    private final String identityCode;
    private final Class<I> identityType;
    private final BusinessIdentityResolver<I> resolver;

    public BusinessIdentityRegistration(
        String identityCode,
        Class<I> identityType,
        BusinessIdentityResolver<I> resolver
    ) {
        if (identityCode == null || identityCode.trim().isEmpty() || !identityCode.equals(identityCode.trim())) {
            throw new ValidationException("身份注册 code 不能为空或包含首尾空格");
        }
        if (identityType == null || !BusinessIdentity.class.isAssignableFrom(identityType)
            || identityType.isInterface() || Modifier.isAbstract(identityType.getModifiers())) {
            throw new ValidationException("身份注册必须指定具体 BusinessIdentity 类型: " + identityCode);
        }
        if (resolver == null) {
            throw new ValidationException("身份注册必须提供可信解析器: " + identityCode);
        }
        this.identityCode = identityCode;
        this.identityType = identityType;
        this.resolver = resolver;
    }

    public String getIdentityCode() {
        return identityCode;
    }

    public Class<I> getIdentityType() {
        return identityType;
    }

    public BusinessIdentityResolver<I> getResolver() {
        return resolver;
    }
}
