package com.entloom.crud.starter.config;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentityDefinition;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.starter.web.access.HttpAccessAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
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
        if (properties.getAccounts().isEmpty()) return;
        properties.getRoutes().forEach((pattern, entry) -> routes.put(
            PathPatternParser.defaultInstance.parse(requireText(pattern, "路由模式")), requireText(entry, "路由入口")));
        Map<String, BusinessIdentityDefinition> fields = new LinkedHashMap<>();
        identities.definitions().values().forEach(definition -> {
            String field = normalize(definition.getIdField());
            if (field.equals("token") || field.equals("subjectid") || fields.putIfAbsent(field, definition) != null) {
                throw new IllegalArgumentException("演示账号身份字段重复或与凭证字段冲突: " + definition.getIdField());
            }
        });
        for (Map<String, String> source : properties.getAccounts()) compileAccount(source, fields);
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

    private void compileAccount(Map<String, String> source, Map<String, BusinessIdentityDefinition> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (values.putIfAbsent(normalize(key), requireText(value, "账号字段 " + key)) != null) {
                throw new IllegalArgumentException("演示账号字段重复: " + key);
            }
        });
        String token = requireText(values.remove("token"), "账号 token");
        String subjectId = requireText(values.remove("subjectid"), "账号 subject-id");
        if (values.size() != 1) throw new IllegalArgumentException("演示账号必须且只能声明一个业务身份 ID 字段");
        Map.Entry<String, String> coordinate = values.entrySet().iterator().next();
        BusinessIdentityDefinition definition = fields.get(coordinate.getKey());
        if (definition == null) throw new IllegalArgumentException("演示账号引用未定义的身份字段: " + coordinate.getKey());
        String id = definition.snapshot(coordinate.getValue()).getIdentityId();
        String code = definition.getCode();
        if (subjects.putIfAbsent(subjectId, new Account(code, id)) != null) {
            throw new IllegalArgumentException("演示账号主体必须唯一");
        }
        CredentialAuthentication authentication = CredentialAuthentication.authenticated(
            new AccessSubject(subjectId, AccessSubjectType.HUMAN), code, id);
        if (credentials.putIfAbsent("Bearer " + token, authentication) != null) {
            throw new IllegalArgumentException("演示账号令牌必须唯一");
        }
    }

    private static String normalize(String value) {
        return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("演示" + name + "不能为空或包含首尾空格");
        }
        return value;
    }

    private record Account(String code, String id) {}
}
