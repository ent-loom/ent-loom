package com.example.minicommerce;

import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.starter.config.CrudAccessProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 商城共享身份、归属与访问策略。 */
@Configuration(proxyBeanMethods = false)
public class CommerceAccessConfiguration {
    @Bean
    CrudAccessProperties commerceAccessProperties() {
        CrudAccessProperties access = new CrudAccessProperties();
        identity(access, "customer", null);
        identity(access, "merchant", null);
        identity(access, "super-admin", "superAdminId");
        relation(access, "customer", "customerId");
        relation(access, "merchant", "merchantId");
        access.setDefaultPolicy("maintainable");
        subject(access.getDefaults(), "super-admin", List.of("*"), "all");
        subject(access.getDefaults(), "merchant", null, "merchant");
        subject(access.getDefaults(), "customer", null, "customer");
        policy(access, "maintainable", List.of("view", "create", "update", "delete"));
        policy(access, "workflow", List.of("view"));
        policy(access, "record", List.of("view"));
        return access;
    }

    private void identity(CrudAccessProperties access, String code, String field) {
        var identity = new CrudAccessProperties.Identity();
        identity.setIdType(BusinessIdentityIdType.LONG);
        identity.setIdField(field);
        access.getIdentities().put(code, identity);
    }

    private void relation(CrudAccessProperties access, String code, String field) {
        var relation = new CrudAccessProperties.Relation();
        relation.setField(field);
        access.getRelations().put(code, relation);
    }

    private void policy(CrudAccessProperties access, String code, List<String> actions) {
        var policy = new CrudAccessProperties.Policy();
        policy.setActions(actions);
        subject(policy, "merchant", actions, null);
        subject(policy, "customer", actions, null);
        access.getPolicies().put(code, policy);
    }

    private void subject(CrudAccessProperties.Policy policy, String identity, List<String> actions, String scope) {
        var subject = new CrudAccessProperties.Subject();
        if (actions != null) subject.setActions(actions);
        if (scope != null) subject.setScope(scope);
        policy.getAccess().put(identity, subject);
    }
}
