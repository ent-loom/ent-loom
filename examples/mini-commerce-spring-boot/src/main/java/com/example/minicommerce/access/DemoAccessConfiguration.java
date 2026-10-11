package com.example.minicommerce.access;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistration;
import com.entloom.crud.starter.web.access.CrudHttpAuthenticationSource;
import com.entloom.crud.starter.web.access.HttpAccessAuthentication;
import com.example.minicommerce.customer.access.CustomerDemoProperties;
import com.example.minicommerce.customer.access.CustomerIdentity;
import com.example.minicommerce.merchant.access.MerchantDemoProperties;
import com.example.minicommerce.merchant.access.MerchantIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 两端共用可信凭证来源，当前身份由令牌绑定，业务入口由服务端路由确定。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CustomerDemoProperties.class, MerchantDemoProperties.class})
public class DemoAccessConfiguration {
    @Bean
    CrudHttpAuthenticationSource demoAuthentication(MerchantDemoProperties merchants, CustomerDemoProperties customers) {
        Map<String, CredentialAuthentication> credentials = credentials(merchants, customers);
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
                .filter(account -> belongsTo(subject, account.subjectId(), account.customerId(), id))
                .findFirst().map(account -> new CustomerIdentity(account.customerId()))
                .orElseThrow(() -> new PermissionDeniedException("账号不属于当前客户")));
    }

    @Bean
    BusinessIdentityRegistration<MerchantIdentity> merchantIdentity(MerchantDemoProperties properties) {
        return new BusinessIdentityRegistration<>(MerchantIdentity.CODE, MerchantIdentity.class, (subject, id) ->
            properties.accounts().stream()
                .filter(account -> belongsTo(subject, account.subjectId(), account.merchantId(), id))
                .findFirst().map(account -> new MerchantIdentity(account.merchantId()))
                .orElseThrow(() -> new PermissionDeniedException("账号不属于当前商家")));
    }

    private static Map<String, CredentialAuthentication> credentials(MerchantDemoProperties merchants,
        CustomerDemoProperties customers) {
        Map<String, CredentialAuthentication> credentials = new HashMap<>();
        Set<String> subjects = new HashSet<>();
        demoAccounts(merchants, customers).forEach(account -> {
            if (!subjects.add(account.subjectId())) {
                throw new IllegalArgumentException("演示账号主体必须唯一");
            }
            register(credentials, account);
        });
        return credentials;
    }

    private static List<DemoAccount> demoAccounts(MerchantDemoProperties merchants,
        CustomerDemoProperties customers) {
        return Stream.concat(
            merchants.accounts().stream().map(account ->
                new DemoAccount(account.token(), account.subjectId(), MerchantIdentity.CODE, account.merchantId())),
            customers.accounts().stream().map(account ->
                new DemoAccount(account.token(), account.subjectId(), CustomerIdentity.CODE, account.customerId()))
        ).toList();
    }

    private static void register(Map<String, CredentialAuthentication> credentials, DemoAccount account) {
        var authentication = CredentialAuthentication.authenticated(
            new AccessSubject(account.subjectId(), AccessSubjectType.HUMAN), account.identityCode(),
            account.identityId().toString());
        if (credentials.putIfAbsent("Bearer " + account.token(), authentication) != null) {
            throw new IllegalArgumentException("两端演示账号的令牌必须唯一");
        }
    }

    private static boolean belongsTo(AccessSubject subject, String subjectId, Long identityId, String requestedId) {
        return subject.getType() == AccessSubjectType.HUMAN
            && subjectId.equals(subject.getSubjectId()) && identityId.toString().equals(requestedId);
    }

    private record DemoAccount(String token, String subjectId, String identityCode, Long identityId) {}
}
