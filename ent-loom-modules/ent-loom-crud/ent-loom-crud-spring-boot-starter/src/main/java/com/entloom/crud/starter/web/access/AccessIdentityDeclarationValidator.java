package com.entloom.crud.starter.web.access;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 启动时校验实际 MVC 入口的身份引用；类与方法声明采用同一覆盖规则。 */
public final class AccessIdentityDeclarationValidator implements SmartInitializingSingleton {
    private final BusinessIdentityRegistry identities;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    public AccessIdentityDeclarationValidator(BusinessIdentityRegistry identities,
        ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.identities = identities;
        this.mappings = mappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        mappings.orderedStream().forEach(mapping -> mapping.getHandlerMethods().values().forEach(this::validate));
    }

    private void validate(HandlerMethod handler) {
        EntAccess access = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), EntAccess.class);
        if (access == null) access = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), EntAccess.class);
        if (access == null || access.identity().isEmpty()) return;
        String code = access.identity();
        if (!code.equals(code.trim()) || !identities.snapshot().containsKey(code)) {
            throw new ValidationException("@EntAccess 引用未注册或不规范的身份码: " + code + "，入口: " + handler.getMethod());
        }
    }
}
