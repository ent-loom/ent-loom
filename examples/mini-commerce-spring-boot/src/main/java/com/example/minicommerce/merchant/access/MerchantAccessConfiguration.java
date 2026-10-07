package com.example.minicommerce.merchant.access;

import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 展示项目如何配置身份、静态策略及每次授权的数据范围和字段能力。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MerchantDemoProperties.class)
public class MerchantAccessConfiguration {
    public static final BusinessOperation VIEW = new BusinessOperation("merchantProduct", "view");
    public static final BusinessOperation UPDATE = new BusinessOperation("merchantProduct", "update");
    private static final String SOURCE = "mini-commerce:merchant";

    @Bean
    BusinessIdentityRegistration<MerchantIdentity> merchantIdentity(MerchantDemoProperties properties) {
        return new BusinessIdentityRegistration<>(MerchantIdentity.CODE, MerchantIdentity.class, (subject, id) ->
            properties.accounts().stream()
                .filter(account -> subject.getType() == AccessSubjectType.HUMAN
                    && account.subjectId().equals(subject.getSubjectId()) && account.merchantId().toString().equals(id))
                .findFirst().map(account -> new MerchantIdentity(account.merchantId()))
                .orElseThrow(() -> new PermissionDeniedException("账号不属于当前商家")));
    }

    @Bean
    BusinessOperationDeclaration merchantView() {
        return new BusinessOperationDeclaration(VIEW, SOURCE);
    }

    @Bean
    BusinessOperationDeclaration merchantUpdate() {
        return new BusinessOperationDeclaration(UPDATE, SOURCE);
    }

    @Bean
    EntityPolicyTemplate merchantPolicy() {
        var branch = PolicyIdentityBranch.identity(MerchantIdentity.CODE);
        return new EntityPolicyTemplate("merchant-pricing", SOURCE, List.of(
            new EntityPolicyTemplate.Rule("view", branch, List.of()),
            new EntityPolicyTemplate.Rule("update", branch, List.of())));
    }

    @Bean
    EntityPolicyBinding merchantPolicyBinding() {
        return new EntityPolicyBinding(MerchantProduct.class, "merchant-pricing", SOURCE, Map.of());
    }

    @Bean
    CrudOperationMapping merchantListMapping() {
        return new CrudOperationMapping(CrudOperationKey.of(QueryOperation.LIST), null, VIEW, SOURCE);
    }
}
