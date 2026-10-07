package com.entloom.crud.starter.web.access;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.concurrent.Callable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;

/** 对实际 Handler 声明执行准入，与参数是否注入无关；不替代 Filter 认证或业务操作授权。 */
public final class AccessAdmissionInterceptor implements AsyncHandlerInterceptor {
    private final AccessAdmissionService admissionService;
    private final AccessHttpErrorWriter errorWriter;
    private final AccessContextArgumentResolver argumentResolver = new AccessContextArgumentResolver();

    public AccessAdmissionInterceptor(AccessAdmissionService admissionService, AccessHttpErrorWriter errorWriter) {
        this.admissionService = admissionService;
        this.errorWriter = errorWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        AccessWebRequestContext state = AccessWebRequestContext.find(request);
        EntAccess access = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), EntAccess.class);
        if (access == null) {
            access = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), EntAccess.class);
        }
        try {
            if (access == null) {
                for (MethodParameter parameter : method.getMethodParameters()) {
                    if (argumentResolver.supportsParameter(parameter)) {
                        throw new PermissionDeniedException("上下文接口必须声明 @EntAccess");
                    }
                }
                if (state != null) state.beginDispatch(method.getMethod(), null);
                return true;
            }
            if (state == null) {
                throw new PermissionDeniedException("缺少 HTTP 可信认证适配器或 Filter 认证结果");
            }
            HttpAccessAuthentication authentication = state.authentication();
            AccessAdmission admission = admissionService.admit(access.value(), authentication::getAuthentication,
                authentication.getAccessEntry(), authentication.getRequestId(), authentication.getTraceId());
            requireIdentity(admission, access.identity());
            requireTrustedSource(admission, access);
            for (MethodParameter parameter : method.getMethodParameters()) {
                if (argumentResolver.supportsParameter(parameter) && parameter.getParameterAnnotations().length > 0) {
                    throw new ValidationException("可信上下文参数不能声明请求绑定注解: " + method.getMethod());
                }
                if (parameter.getParameterType() == AccessContext.class) {
                    Class<? extends BusinessIdentity> identityType = AccessContextArgumentResolver.identityType(parameter);
                    if (identityType != access.identity()) {
                        throw new ValidationException("固定身份参数必须与 @EntAccess.identity 声明一致: " + method.getMethod());
                    }
                }
            }
            state.beginDispatch(method.getMethod(), admission);
            WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(getClass().getName(),
                new AuthorizationBoundaryInterceptor());
            return true;
        } catch (CrudException exception) {
            errorWriter.write(response, exception);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        closeScope(request, handler);
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        closeScope(request, handler);
    }

    private static void closeScope(HttpServletRequest request, Object handler) {
        AccessWebRequestContext state = AccessWebRequestContext.find(request);
        if (state != null && handler instanceof HandlerMethod) {
            state.endDispatch(((HandlerMethod) handler).getMethod());
        }
    }

    private static void requireIdentity(AccessAdmission admission, Class<? extends BusinessIdentity> identityType) {
        if (identityType != BusinessIdentity.class && !admission.getVisitorContext().findIdentity(identityType).isPresent()) {
            throw new PermissionDeniedException("当前身份不满足接口要求: " + identityType.getName());
        }
    }

    /** 工作线程只保留声明接口的授权要求，避免未绑定资源借异步调用退回旧链。 */
    private static final class AuthorizationBoundaryInterceptor implements CallableProcessingInterceptor {
        private AccessContextScope.Scope scope;

        @Override
        public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
            scope = AccessContextScope.requireAuthorization();
        }

        @Override
        public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) {
            if (scope != null) {
                scope.close();
                scope = null;
            }
        }
    }

    private static void requireTrustedSource(AccessAdmission admission, EntAccess access) {
        String entry = admission.getVisitorContext().getAccessEntry();
        for (String allowed : access.accessEntries()) {
            if (allowed == null || allowed.isEmpty() || !allowed.equals(allowed.trim())) {
                throw new ValidationException("@EntAccess.accessEntries 必须使用非空规范入口 code");
            }
        }
        if (access.accessEntries().length > 0 && !Arrays.asList(access.accessEntries()).contains(entry)) {
            throw new PermissionDeniedException("可信入口不满足接口要求");
        }
        AccessSubjectType[] types = access.subjectTypes();
        if (types.length > 0 && !admission.getVisitorContext().getSubject()
            .map(subject -> Arrays.asList(types).contains(subject.getType())).orElse(false)) {
            throw new PermissionDeniedException("主体来源类型不满足接口要求");
        }
    }
}
