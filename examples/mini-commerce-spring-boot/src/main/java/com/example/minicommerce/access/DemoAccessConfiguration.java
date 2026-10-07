package com.example.minicommerce.access;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.starter.web.access.CrudHttpAuthenticationSource;
import com.entloom.crud.starter.web.access.HttpAccessAuthentication;
import com.example.minicommerce.customer.access.CustomerDemoProperties;
import com.example.minicommerce.customer.access.CustomerIdentity;
import com.example.minicommerce.merchant.access.MerchantDemoProperties;
import com.example.minicommerce.merchant.access.MerchantAccessConfiguration;
import com.example.minicommerce.merchant.access.MerchantIdentity;
import com.example.minicommerce.order.access.OrderAccessConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 两端共用可信凭证来源，当前身份由令牌绑定，业务入口由服务端路由确定。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerDemoProperties.class)
public class DemoAccessConfiguration {
    @Bean
    CrudHttpAuthenticationSource demoAuthentication(MerchantDemoProperties merchants, CustomerDemoProperties customers) {
        Map<String, CredentialAuthentication> credentials = new HashMap<>();
        var merchantSubjects = new HashSet<String>();
        for (var account : merchants.accounts()) {
            if (!merchantSubjects.add(account.subjectId())) {
                throw new IllegalArgumentException("演示商家账号主体必须唯一");
            }
            register(credentials, account.token(), account.subjectId(), MerchantIdentity.CODE, account.merchantId());
        }
        var customerSubjects = new HashSet<String>();
        for (var account : customers.accounts()) {
            if (!customerSubjects.add(account.subjectId())) {
                throw new IllegalArgumentException("演示用户账号主体必须唯一");
            }
            register(credentials, account.token(), account.subjectId(), CustomerIdentity.CODE, account.customerId());
        }
        return request -> {
            String header = request.getHeader("Authorization");
            var authentication = header == null ? CredentialAuthentication.absent()
                : credentials.getOrDefault(header, CredentialAuthentication.invalid());
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String entry = path.startsWith("/api/merchant/") ? "merchant" : "consumer";
            return new HttpAccessAuthentication(authentication, entry, UUID.randomUUID().toString(), null);
        };
    }

    @Bean
    BusinessIdentityRegistration<CustomerIdentity> customerIdentity(CustomerDemoProperties properties) {
        return new BusinessIdentityRegistration<>(CustomerIdentity.CODE, CustomerIdentity.class, (subject, id) ->
            properties.accounts().stream()
                .filter(account -> subject.getType() == AccessSubjectType.HUMAN
                    && account.subjectId().equals(subject.getSubjectId()) && account.customerId().toString().equals(id))
                .findFirst().map(account -> new CustomerIdentity(account.customerId()))
                .orElseThrow(() -> new PermissionDeniedException("账号不属于当前客户")));
    }

    @Bean
    BusinessPermissionEvaluator commercePermissions() {
        return (visitor, operation, rule, targetId) -> {
            var merchant = visitor.findIdentity(MerchantIdentity.class);
            var customer = visitor.findIdentity(CustomerIdentity.class);
            if (OrderAccessConfiguration.VIEW.equals(operation)) {
                var returned = List.of("id", "orderId", "customerId", "merchantId", "totalAmount");
                if (merchant.isPresent() && "merchant".equals(visitor.getAccessEntry())) {
                    return BusinessPermissionDecision.allow(
                        AuthorizationScope.equal("merchantId", merchant.get().merchantId()),
                        AuthorizationFields.of(Map.of(
                            AuthorizationFields.Use.RETURN, returned,
                            AuthorizationFields.Use.FILTER, List.of("customerId"),
                            AuthorizationFields.Use.SORT, List.of("id"))));
                }
                if (customer.isPresent() && "consumer".equals(visitor.getAccessEntry())) {
                    return BusinessPermissionDecision.allow(
                        AuthorizationScope.equal("customerId", customer.get().customerId()),
                        AuthorizationFields.of(Map.of(
                            AuthorizationFields.Use.RETURN, returned,
                            AuthorizationFields.Use.FILTER, List.of("merchantId"),
                            AuthorizationFields.Use.SORT, List.of("id"))));
                }
            }
            if (merchant.isPresent() && "merchant".equals(visitor.getAccessEntry())
                && (MerchantAccessConfiguration.VIEW.equals(operation) || MerchantAccessConfiguration.UPDATE.equals(operation))) {
                return BusinessPermissionDecision.allow(
                    AuthorizationScope.equal("merchantId", merchant.get().merchantId()),
                    AuthorizationFields.of(Map.of(
                        AuthorizationFields.Use.RETURN, List.of("id", "productId", "merchantId", "price", "version"),
                        AuthorizationFields.Use.WRITE, List.of("price"))));
            }
            return BusinessPermissionDecision.deny();
        };
    }

    private static void register(Map<String, CredentialAuthentication> credentials, String token, String subjectId,
        String identityCode, Long identityId) {
        var authentication = CredentialAuthentication.authenticated(
            new AccessSubject(subjectId, AccessSubjectType.HUMAN), identityCode, identityId.toString());
        if (credentials.putIfAbsent("Bearer " + token, authentication) != null) {
            throw new IllegalArgumentException("两端演示账号的令牌必须唯一");
        }
    }
}
