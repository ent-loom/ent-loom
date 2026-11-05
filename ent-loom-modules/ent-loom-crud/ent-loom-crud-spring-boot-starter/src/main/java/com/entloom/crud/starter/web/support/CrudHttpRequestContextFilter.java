package com.entloom.crud.starter.web.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在认证之前创建请求标识，并在异步工作线程中恢复。 */
public final class CrudHttpRequestContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        CrudHttpRequestContext context = CrudHttpRequestContext.forRequest(request);
        WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(getClass().getName(),
            new CallableProcessingInterceptor() {
                private CrudHttpRequestContext.Scope scope;
                @Override public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
                    scope = context.open();
                }
                @Override public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object result) {
                    if (scope != null) { scope.close(); scope = null; }
                }
            });
        try (CrudHttpRequestContext.Scope ignored = context.open()) {
            chain.doFilter(request, response);
        }
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
}
