package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.starter.web.access.HttpAccessAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** 显式演示模式的静态账号编译；身份结构复用 YAML 定义，归属绑定来自服务端账号表。 */
public final class ConfiguredExampleAccounts {
    private final Map<String, CredentialAuthentication> credentials = new LinkedHashMap<>();
    private final Map<String, Account> subjects = new LinkedHashMap<>();
    private final Map<PathPattern, String> routes = new LinkedHashMap<>();
    private final String defaultEntry;

    public ConfiguredExampleAccounts(CrudExampleProperties properties, ConfiguredBusinessIdentities identities) {
        defaultEntry = requireText(properties.getDefaultAccessEntry(), "默认入口");
        properties.getRoutes().forEach(route -> {
            if (route == null) throw new IllegalArgumentException("演示路由规则不能为空");
            PathPattern pattern = PathPatternParser.defaultInstance.parse(requireText(route.getPath(), "路由 path"));
            String entry = requireText(route.getEntry(), "路由 entry");
            String previous = routes.putIfAbsent(pattern, entry);
            if (previous != null) throw new IllegalArgumentException("演示路由 path 重复: " + route.getPath());
        });
        for (CrudExampleProperties.Account source : properties.getAccounts()) compileAccount(source, identities.definitions());
    }

    /** 每次准入重新核对已认证主体、身份码和 ID，不按身份类型匹配。 */
    public boolean belongsTo(AccessSubject subject, String code, String id) {
        Account account = subjects.get(subject.getSubjectId());
        return subject.getType() == AccessSubjectType.HUMAN && account != null
            && code.equals(account.code()) && id.equals(account.id());
    }

    public HttpAccessAuthentication authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        CredentialAuthentication authentication = header == null ? CredentialAuthentication.absent()
            : credentials.getOrDefault(header, CredentialAuthentication.invalid());
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        String entry = null;
        for (Map.Entry<PathPattern, String> route : routes.entrySet()) {
            if (!route.getKey().matches(path)) continue;
            if (entry != null && !entry.equals(route.getValue())) {
                throw new PermissionDeniedException("演示路由匹配到多个可信入口");
            }
            entry = route.getValue();
        }
        return new HttpAccessAuthentication(authentication, entry == null ? defaultEntry : entry,
            UUID.randomUUID().toString(), null);
    }

    private void compileAccount(CrudExampleProperties.Account source, Map<String, BusinessIdentityDefinition> identities) {
        if (source == null || source.getIdentity() == null) throw new IllegalArgumentException("演示账号必须声明 identity");
        String token = requireText(source.getToken(), "账号 token");
        String subjectId = requireText(source.getSubjectId(), "账号 subject-id");
        String code = requireText(source.getIdentity().getCode(), "账号 identity.code");
        BusinessIdentityDefinition definition = identities.get(code);
        if (definition == null) throw new IllegalArgumentException("演示账号引用未定义的身份码: " + code);
        String id = definition.snapshot(requireText(source.getIdentity().getId(), "账号 identity.id")).getIdentityId();
        if (subjects.putIfAbsent(subjectId, new Account(code, id)) != null) {
            throw new IllegalArgumentException("演示账号主体必须唯一");
        }
        CredentialAuthentication authentication = CredentialAuthentication.authenticated(
            new AccessSubject(subjectId, AccessSubjectType.HUMAN), code, id);
        if (credentials.putIfAbsent("Bearer " + token, authentication) != null) {
            throw new IllegalArgumentException("演示账号令牌必须唯一");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("演示" + name + "不能为空或包含首尾空格");
        }
        return value;
    }

    private record Account(String code, String id) {}
}
