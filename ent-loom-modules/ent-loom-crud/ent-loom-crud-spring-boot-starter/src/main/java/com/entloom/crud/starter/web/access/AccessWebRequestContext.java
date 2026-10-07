package com.entloom.crud.starter.web.access;

import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/** 仅在当前 Servlet 分派内保存可信认证、准入结果和同步作用域句柄。 */
final class AccessWebRequestContext {
    private static final String ATTRIBUTE = AccessWebRequestContext.class.getName();
    private final HttpAccessAuthentication authentication;
    private final Deque<Dispatch> dispatches = new ArrayDeque<>();

    private AccessWebRequestContext(HttpAccessAuthentication authentication) {
        this.authentication = authentication;
    }

    static void bind(HttpServletRequest request, HttpAccessAuthentication authentication) {
        request.setAttribute(ATTRIBUTE, new AccessWebRequestContext(authentication));
    }

    static AccessWebRequestContext find(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof AccessWebRequestContext ? (AccessWebRequestContext) value : null;
    }

    static void clear(HttpServletRequest request) {
        request.removeAttribute(ATTRIBUTE);
    }

    HttpAccessAuthentication authentication() {
        return authentication;
    }

    void beginDispatch(Method method, AccessAdmission admission) {
        AccessContextScope.Scope scope = admission == null
            ? AccessContextScope.suspend() : AccessContextScope.enter(admission);
        dispatches.push(new Dispatch(method, admission, scope));
    }

    void endDispatch(Method method) {
        if (dispatches.isEmpty()) return;
        Dispatch dispatch = dispatches.peek();
        if (!dispatch.method.equals(method)) {
            throw new IllegalStateException("MVC 分派必须按嵌套顺序结束");
        }
        dispatch.scope.close();
        dispatches.pop();
    }

    void closeScopes() {
        while (!dispatches.isEmpty()) {
            endDispatch(dispatches.peek().method);
        }
    }

    AccessAdmission admission(Method method) {
        Dispatch dispatch = dispatches.peek();
        return dispatch != null && method.equals(dispatch.method) ? dispatch.admission : null;
    }

    private static final class Dispatch {
        private final Method method;
        private final AccessAdmission admission;
        private final AccessContextScope.Scope scope;

        private Dispatch(Method method, AccessAdmission admission, AccessContextScope.Scope scope) {
            this.method = method;
            this.admission = admission;
            this.scope = scope;
        }
    }
}
