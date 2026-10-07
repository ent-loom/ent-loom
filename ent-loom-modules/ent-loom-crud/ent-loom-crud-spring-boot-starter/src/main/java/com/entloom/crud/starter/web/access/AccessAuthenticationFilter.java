package com.entloom.crud.starter.web.access;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 外层及异步/错误分派验证凭证；嵌套 forward 复用认证、由 MVC 独立准入，结束时清理上下文。 */
public final class AccessAuthenticationFilter extends OncePerRequestFilter {
    private final CrudHttpAuthenticationSource source;
    private final AccessHttpErrorWriter errorWriter;

    public AccessAuthenticationFilter(CrudHttpAuthenticationSource source, AccessHttpErrorWriter errorWriter) {
        this.source = source;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        AccessWebRequestContext.clear(request);
        HttpAccessAuthentication authentication;
        try {
            authentication = source.authenticate(request);
        } catch (PermissionDeniedException exception) {
            errorWriter.write(response, new PermissionDeniedException("凭证认证失败"));
            return;
        } catch (RuntimeException exception) {
            errorWriter.write(response, new CrudException(CrudErrorCode.INTERNAL_ERROR, "可信认证来源处理失败"));
            return;
        }
        if (authentication == null || authentication.getAuthentication().getStatus() == CredentialAuthentication.Status.INVALID) {
            errorWriter.write(response, new PermissionDeniedException("凭证无效、已过期或认证来源未返回结果"));
            return;
        }
        AccessWebRequestContext.bind(request, authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                AccessWebRequestContext state = AccessWebRequestContext.find(request);
                if (state != null) state.closeScopes();
            } finally {
                AccessWebRequestContext.clear(request);
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
