package com.example.customerprofile;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local-only governance wiring. Replace this with the application's identity and scope adapters in production.
 */
@Configuration
@Profile("example")
public class DevelopmentGovernanceConfiguration {
    @Bean
    CrudSubjectResolver developmentSubjectResolver() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("local-developer");
        return () -> subject;
    }

    @Bean
    CrudDataScopeResolver developmentDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }
}
