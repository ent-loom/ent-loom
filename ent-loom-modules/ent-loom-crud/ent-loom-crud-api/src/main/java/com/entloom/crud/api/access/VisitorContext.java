package com.entloom.crud.api.access;

import java.util.Objects;
import java.util.Optional;

/**
 * 显式传递的访问者上下文，不承载业务 payload 或授权结果。
 *
 * <p>只能由可信接入适配器在认证后构建，不能从请求 body 或属性 Map 反序列化为认证依据。
 * 工厂仅校验结构，不验证凭证、身份归属或项目注册。入口仅表达接入限制，不产生权限。</p>
 */
public final class VisitorContext {
    private final AccessSubject subject;
    private final BusinessIdentity identity;
    private final String accessEntry;
    private final String requestId;
    private final String traceId;

    private VisitorContext(
        AccessSubject subject,
        BusinessIdentity identity,
        String accessEntry,
        String requestId,
        String traceId
    ) {
        if (accessEntry == null || accessEntry.trim().isEmpty()) {
            throw new IllegalArgumentException("可信入口 accessEntry 不能为空");
        }
        if (identity != null) {
            if (identity.getIdentityCode() == null || identity.getIdentityCode().trim().isEmpty()) {
                throw new IllegalArgumentException("当前身份 code 不能为空");
            }
            if (identity.getIdentityId() == null || identity.getIdentityId().trim().isEmpty()) {
                throw new IllegalArgumentException("当前身份 ID 不能为空");
            }
        }
        this.subject = subject;
        this.identity = identity;
        this.accessEntry = accessEntry.trim();
        this.requestId = normalizeOptional(requestId);
        this.traceId = normalizeOptional(traceId);
    }

    public static VisitorContext anonymous(String accessEntry, String requestId, String traceId) {
        return new VisitorContext(null, null, accessEntry, requestId, traceId);
    }

    /** 已认证但尚未选择业务身份时，identity 可以为空。 */
    public static VisitorContext authenticated(
        AccessSubject subject,
        BusinessIdentity identity,
        String accessEntry,
        String requestId,
        String traceId
    ) {
        return new VisitorContext(Objects.requireNonNull(subject, "已认证主体不能为空"),
            identity, accessEntry, requestId, traceId);
    }

    public boolean isAnonymous() {
        return subject == null;
    }

    public Optional<AccessSubject> getSubject() {
        return Optional.ofNullable(subject);
    }

    public Optional<BusinessIdentity> getIdentity() {
        return Optional.ofNullable(identity);
    }

    /** 仅检查当前身份，不搜索账号的其他身份，也不触发身份切换。 */
    public Optional<BusinessIdentity> findIdentity(String identityCode) {
        Objects.requireNonNull(identityCode, "身份码不能为空");
        if (identityCode.trim().isEmpty() || !identityCode.equals(identityCode.trim())) {
            throw new IllegalArgumentException("身份码不能为空或包含首尾空格");
        }
        return identity != null && identityCode.equals(identity.getIdentityCode()) ? Optional.of(identity) : Optional.empty();
    }

    public BusinessIdentity requireIdentity(String identityCode) {
        return findIdentity(identityCode).orElseThrow(() ->
            new IllegalStateException("当前身份不匹配: " + identityCode));
    }

    public String getAccessEntry() {
        return accessEntry;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
