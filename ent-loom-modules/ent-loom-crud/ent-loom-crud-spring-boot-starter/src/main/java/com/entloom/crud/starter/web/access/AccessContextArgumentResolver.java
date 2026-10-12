package com.entloom.crud.starter.web.access;

import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 只注入当前 Handler 已获准入的上下文，不读取 body、Header 或普通请求属性中的身份。 */
public final class AccessContextArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == VisitorContext.class || parameter.getParameterType() == AccessContext.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        AccessWebRequestContext state = request == null ? null : AccessWebRequestContext.find(request);
        AccessAdmission admission = state == null ? null : state.admission(parameter.getMethod());
        if (admission == null) {
            throw new PermissionDeniedException("当前接口尚未完成可信准入");
        }
        VisitorContext visitor = admission.getVisitorContext();
        return parameter.getParameterType() == VisitorContext.class ? visitor : AccessContext.from(visitor, identityCode(parameter));
    }

    static String identityCode(MethodParameter parameter) {
        EntAccess access = AnnotatedElementUtils.findMergedAnnotation(parameter.getMethod(), EntAccess.class);
        if (access == null) access = AnnotatedElementUtils.findMergedAnnotation(parameter.getContainingClass(), EntAccess.class);
        if (access == null || access.identity().isEmpty() || !access.identity().equals(access.identity().trim())) {
            throw new ValidationException("AccessContext 参数必须由 @EntAccess.identity 声明身份码: " + parameter.getMethod());
        }
        return access.identity();
    }
}
