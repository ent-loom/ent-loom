package com.entloom.crud.starter.web.access;

import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import java.util.Objects;

/** 可信 HTTP 适配器返回的不可变认证结果与接入元数据，不携带凭证原文或业务 payload。 */
public final class HttpAccessAuthentication {
    private final CredentialAuthentication authentication;
    private final String accessEntry;
    public HttpAccessAuthentication(CredentialAuthentication authentication, String accessEntry) {
        this.authentication = Objects.requireNonNull(authentication, "HTTP 凭证认证结果不能为空");
        if (accessEntry == null || accessEntry.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP 可信入口不能为空");
        }
        this.accessEntry = accessEntry.trim();
    }

    public CredentialAuthentication getAuthentication() {
        return authentication;
    }

    public String getAccessEntry() {
        return accessEntry;
    }

}
