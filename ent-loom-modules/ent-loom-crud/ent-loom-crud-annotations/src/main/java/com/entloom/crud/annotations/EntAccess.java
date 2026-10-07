package com.entloom.crud.annotations;

import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.BusinessIdentity;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 接口认证与当前身份准入声明；不替代资源操作授权，方法声明覆盖类声明。 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EntAccess {
    AuthenticationMode value() default AuthenticationMode.REQUIRED;

    Class<? extends BusinessIdentity> identity() default BusinessIdentity.class;

    /** 允许的可信入口；空数组表示不增加入口限制。 */
    String[] accessEntries() default {};

    /** 允许的已认证主体来源；空数组表示不增加来源类型限制。 */
    AccessSubjectType[] subjectTypes() default {};
}
