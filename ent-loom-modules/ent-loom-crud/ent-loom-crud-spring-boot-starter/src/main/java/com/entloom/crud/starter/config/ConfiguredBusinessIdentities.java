package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.api.access.ConfiguredBusinessIdentity;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 冻结 YAML 身份定义，按业务码装配可信解析器，不扫描或推断业务类。 */
public final class ConfiguredBusinessIdentities {
    private final Map<String, BusinessIdentityDefinition> definitions;

    public ConfiguredBusinessIdentities(CrudAccessProperties properties) {
        Map<String, BusinessIdentityDefinition> compiled = new LinkedHashMap<>();
        properties.getIdentities().forEach((code, identity) -> {
            if (identity == null) throw new ValidationException("身份定义不能为空: " + code);
            compiled.put(code, new BusinessIdentityDefinition(code,
                identity.getIdField() == null ? code + "Id" : identity.getIdField(), identity.getIdType()));
        });
        definitions = Collections.unmodifiableMap(compiled);
    }

    public Map<String, BusinessIdentityDefinition> definitions() { return definitions; }

    public List<BusinessIdentityRegistration<?>> registrations(List<BusinessIdentityRegistration<?>> explicit,
        ConfiguredExampleAccounts accounts) {
        Map<String, BusinessIdentityRegistration<?>> registrations = new LinkedHashMap<>();
        for (BusinessIdentityRegistration<?> registration : explicit) {
            if (registrations.putIfAbsent(registration.getIdentityCode(), registration) != null) {
                throw new ValidationException("身份 code 重复注册: " + registration.getIdentityCode());
            }
        }
        for (BusinessIdentityDefinition definition : definitions.values()) {
            String code = definition.getCode();
            BusinessIdentityRegistration<?> custom = registrations.get(code);
            if (custom != null && custom.getIdentityType() != ConfiguredBusinessIdentity.class) {
                throw new ValidationException("YAML 简单身份须使用统一快照类型，复杂身份只通过 Java 注册: " + code);
            }
            if (custom == null && accounts == null) {
                throw new ValidationException("身份定义缺少可信归属解析器或演示账号来源: " + code);
            }
            registrations.put(code, new BusinessIdentityRegistration<>(code, ConfiguredBusinessIdentity.class, (subject, id) -> {
                ConfiguredBusinessIdentity expected;
                try {
                    expected = definition.snapshot(id);
                } catch (IllegalArgumentException exception) {
                    throw new PermissionDeniedException("当前身份 ID 不符合定义: " + code);
                }
                if (custom == null) return accounts.belongsTo(subject, code, id) ? expected : null;
                BusinessIdentity resolved = custom.getResolver().resolve(subject, id);
                if (resolved != null && !expected.equals(resolved)) {
                    throw new PermissionDeniedException("可信身份解析结果与声明的结构或坐标不一致: " + code);
                }
                return (ConfiguredBusinessIdentity) resolved;
            }));
        }
        return new ArrayList<>(registrations.values());
    }
}
