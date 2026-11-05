package com.entloom.crud.starter.command;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class SpringEntityWriteValidatorTest {
    @Test
    void 创建校验完整实体而更新只校验显式字段() {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("validator", new SmartValidator() {
            @Override public boolean supports(Class<?> type) { return type == Customer.class; }
            @Override public void validate(Object target, Errors errors) {
                if (((Customer) target).email == null) errors.reject("email", "邮箱不能为空");
            }
            @Override public void validate(Object target, Errors errors, Object... hints) { validate(target, errors); }
            @Override public void validateValue(Class<?> type, String field, Object value, Errors errors, Object... hints) {
                if ("email".equals(field) && value == null) errors.reject("email", "邮箱不能为空");
            }
        });
        var validator = new SpringEntityWriteValidator(factory.getBeanProvider(Validator.class));
        var meta = new EntityMeta(Customer.class, new ResourceDescriptor(Customer.class, "customer", null, java.util.List.of()),
            "customer", "id", null, Map.of());
        assertThatThrownBy(() -> validator.validateCreate(meta, Map.of("name", "测试")))
            .isInstanceOf(ValidationException.class).hasMessageContaining("邮箱不能为空");
        assertThatCode(() -> validator.validate(meta, Map.of("name", "测试"))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateCreate(meta, Map.of("name", "测试", "email", "test@example.com")))
            .doesNotThrowAnyException();
    }

    public static class Customer {
        public String name;
        public String email;
    }
}
