package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandAction;
import com.entloom.crud.core.exception.ValidationException;
import org.springframework.beans.BeanUtils;

/** 仅实例化无状态动作定义，策略编译不依赖 Handler 的业务 Bean。 */
public final class CommandActionDefinitions {
    private CommandActionDefinitions() { }

    public static CommandAction<?, ?> resolve(EntCrudCommandAction annotation) {
        try {
            return BeanUtils.instantiateClass(annotation.value());
        } catch (RuntimeException failure) {
            ValidationException error = new ValidationException("无法创建动作定义（须提供无参构造器）: " + annotation.value().getName());
            error.initCause(failure);
            throw error;
        }
    }
}
