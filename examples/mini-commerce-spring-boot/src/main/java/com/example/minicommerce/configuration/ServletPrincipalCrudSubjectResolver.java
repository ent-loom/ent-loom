package com.example.minicommerce.configuration;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 将 Servlet 容器已经完成的认证结果映射为 ent-loom 主体。
 *
 * <p>示例适配器只读取 {@link HttpServletRequest#getUserPrincipal()}，不负责认证、登录、令牌解析或权限判断。
 * 未认证请求使用显式匿名主体，后续由现有权限规则链拒绝。</p>
 */
final class ServletPrincipalCrudSubjectResolver implements CrudSubjectResolver {
    /** 未认证请求使用的显式匿名主体。 */
    static final String ANONYMOUS_SUBJECT_ID = "anonymous";

    private final ObjectProvider<HttpServletRequest> requestProvider;

    ServletPrincipalCrudSubjectResolver(ObjectProvider<HttpServletRequest> requestProvider) {
        if (requestProvider == null) {
            throw new IllegalArgumentException("HttpServletRequest 提供器不能为空");
        }
        this.requestProvider = requestProvider;
    }

    /** 读取当前请求的认证主体。 */
    @Override
    public SubjectContext resolveOrThrow() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        Principal principal = request == null ? null : request.getUserPrincipal();
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(principal == null || principal.getName() == null || principal.getName().isBlank()
            ? ANONYMOUS_SUBJECT_ID : principal.getName());
        return subject;
    }
}
