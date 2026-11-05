package com.example.customerprofile;

import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.starter.config.CrudAccessProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 客户档案管理的身份与共享访问策略。 */
@Configuration(proxyBeanMethods = false)
public class ProfileAccessConfiguration {
    @Bean
    CrudAccessProperties profileAccessProperties() {
        var access = new CrudAccessProperties();
        var identity = new CrudAccessProperties.Identity();
        identity.setIdType(BusinessIdentityIdType.LONG);
        access.getIdentities().put("operator", identity);
        var policy = new CrudAccessProperties.Policy();
        policy.setActions(List.of("view", "create", "update", "delete"));
        var operator = new CrudAccessProperties.Subject();
        operator.setActions(policy.getActions());
        operator.setScope("all");
        policy.getAccess().put("operator", operator);
        access.getPolicies().put("profile-management", policy);
        return access;
    }
}
