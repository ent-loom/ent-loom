package com.example.minicommerce.order.access;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.example.minicommerce.customer.access.CustomerIdentity;
import com.example.minicommerce.merchant.access.MerchantIdentity;
import com.example.minicommerce.order.entity.MerchantOrder;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 同一个 PAGE 对应同一个 view 操作，具体数据范围按可信入口和当前身份判定。 */
@Configuration(proxyBeanMethods = false)
public class OrderAccessConfiguration {
    public static final BusinessOperation VIEW = new BusinessOperation("merchantOrder", "view");
    private static final String SOURCE = "mini-commerce:order-entries";

    @Bean
    BusinessOperationDeclaration orderView() {
        return new BusinessOperationDeclaration(VIEW, SOURCE);
    }

    @Bean
    EntityPolicyTemplate orderPolicy() {
        return new EntityPolicyTemplate("order-entries", SOURCE, List.of(
            new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.identity(CustomerIdentity.CODE), List.of()),
            new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.identity(MerchantIdentity.CODE), List.of())));
    }

    @Bean
    EntityPolicyBinding orderPolicyBinding() {
        return new EntityPolicyBinding(MerchantOrder.class, "order-entries", SOURCE, Map.of());
    }

    @Bean
    CrudOperationMapping orderPageMapping() {
        return new CrudOperationMapping(CrudOperationKey.of(QueryOperation.PAGE), null, VIEW, SOURCE);
    }
}
