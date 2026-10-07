package com.entloom.crud.core.governance.identity;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动期校验并冻结的项目身份注册表。
 *
 * <p>每次解析均重新核验身份事实，不缓存请求身份。主体必须来自已经完成认证的接入适配器；
 * 本注册表不验证凭证，不执行业务操作授权，也不将身份 ID 转换为组织或数据范围。</p>
 */
public final class BusinessIdentityRegistry {
    private final Map<String, BusinessIdentityRegistration<?>> registrations;

    public BusinessIdentityRegistry(Collection<BusinessIdentityRegistration<?>> source) {
        if (source == null) {
            throw new ValidationException("身份注册声明集合不能为空");
        }
        Map<String, BusinessIdentityRegistration<?>> byCode = new LinkedHashMap<String, BusinessIdentityRegistration<?>>();
        Map<Class<?>, String> byType = new LinkedHashMap<Class<?>, String>();
        for (BusinessIdentityRegistration<?> registration : source) {
            if (registration == null) {
                throw new ValidationException("身份注册声明不能为空");
            }
            BusinessIdentityRegistration<?> previous = byCode.put(registration.getIdentityCode(), registration);
            if (previous != null) {
                throw new ValidationException("身份 code 重复注册: " + registration.getIdentityCode()
                    + "，类型: " + previous.getIdentityType().getName() + " / " + registration.getIdentityType().getName());
            }
            String previousCode = byType.put(registration.getIdentityType(), registration.getIdentityCode());
            if (previousCode != null) {
                throw new ValidationException("身份类型重复注册: " + registration.getIdentityType().getName()
                    + "，code: " + previousCode + " / " + registration.getIdentityCode());
            }
        }
        this.registrations = Collections.unmodifiableMap(byCode);
    }

    /** 选择身份时重新解析，拒绝未知 code 以及与请求坐标或注册类型不一致的结果。 */
    public BusinessIdentity resolve(AccessSubject subject, String identityCode, String identityId) {
        if (subject == null) {
            throw new PermissionDeniedException("身份解析需要已认证主体");
        }
        requireCoordinate(identityCode, "code");
        requireCoordinate(identityId, "ID");
        BusinessIdentityRegistration<?> registration = registrations.get(identityCode);
        if (registration == null) {
            throw new PermissionDeniedException("身份 code 未注册: " + identityCode);
        }
        BusinessIdentity identity = registration.getResolver().resolve(subject, identityId);
        if (identity == null) {
            throw new PermissionDeniedException("主体没有有效的当前身份: " + identityCode);
        }
        if (!registration.getIdentityType().equals(identity.getClass())) {
            throw new PermissionDeniedException("身份解析结果类型不匹配: " + identityCode
                + "，注册类型: " + registration.getIdentityType().getName()
                + "，实际类型: " + identity.getClass().getName());
        }
        if (!identityCode.equals(identity.getIdentityCode())) {
            throw new PermissionDeniedException("身份解析结果 code 不匹配: " + identityCode);
        }
        if (!identityId.equals(identity.getIdentityId())) {
            throw new PermissionDeniedException("身份解析结果 ID 不匹配: " + identityCode);
        }
        return identity;
    }

    public Map<String, BusinessIdentityRegistration<?>> snapshot() {
        return registrations;
    }

    private static void requireCoordinate(String value, String name) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim())) {
            throw new PermissionDeniedException("身份选择 " + name + " 不能为空或包含首尾空格");
        }
    }
}
