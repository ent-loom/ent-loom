package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.capability.command.engine.AuthorizedCommandExecutor;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.governance.fact.RelationFactResolver;
import com.entloom.crud.core.governance.fact.RelationFactService;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.query.JdbcAuthorizedQueryExecutor;
import com.entloom.crud.engine.jdbc.command.JdbcAuthorizedCommandExecutor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 汇总 YAML、Java 声明与实体注解，在启动期交由同一 Core 编译器校验。 */
@AutoConfiguration(after = CrudAutoConfiguration.class, before = CrudAccessWebAutoConfiguration.class)
@EnableConfigurationProperties(CrudAccessProperties.class)
@Import(CrudIdentityConfiguration.class)
public class CrudPolicyAutoConfiguration {
    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    public ConfiguredAccessPolicies configuredAccessPolicies(CrudAccessProperties properties,
        EntityMetaRegistry entities, BusinessIdentityRegistry identities, ConfiguredBusinessIdentities definitions) {
        return new ConfiguredAccessPolicies(properties, entities, identities, definitions);
    }


    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public BusinessOperationCatalog businessOperationCatalog(
        EntityMetaRegistry entities,
        ObjectProvider<BusinessOperationDeclaration> declarations,
        ConfiguredAccessPolicies configured
    ) {
        List<ResourceDescriptor> resources = new ArrayList<ResourceDescriptor>();
        for (EntityMeta meta : entities.getEntityMetas()) {
            resources.add(meta.getResourceDescriptor());
        }
        List<BusinessOperationDeclaration> all = new ArrayList<>(declarations.orderedStream().collect(Collectors.toList()));
        all.addAll(configured.operations());
        return new BusinessOperationCatalog(resources, all);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public CrudOperationMappingRegistry crudOperationMappingRegistry(
        EntityMetaRegistry entities,
        BusinessOperationCatalog operations,
        ObjectProvider<CrudOperationMapping> mappings,
        ConfiguredAccessPolicies configured
    ) {
        List<CrudOperationMapping> all = new ArrayList<>(mappings.orderedStream().collect(Collectors.toList()));
        all.addAll(configured.mappings());
        return new CrudOperationMappingRegistry(entities.getEntityMetas(), operations, all);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public BusinessAuthorizationService businessAuthorizationService(
        BusinessOperationCatalog operations,
        EntityPolicyRegistry policies,
        ObjectProvider<BusinessPermissionEvaluator> evaluators,
        ObjectProvider<RelationFactService> facts,
        ConfiguredAccessPolicies configured
    ) {
        BusinessPermissionEvaluator evaluator = configured.evaluator(evaluators.getIfAvailable());
        return new BusinessAuthorizationService(operations, policies, evaluator, facts.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public CrudBusinessAuthorizationAdapter crudBusinessAuthorizationAdapter(
        EntityMetaRegistry entities,
        CrudOperationMappingRegistry mappings,
        BusinessAuthorizationService authorization
    ) {
        return new CrudBusinessAuthorizationAdapter(entities, mappings, authorization);
    }

    /**
     * 将真实业务授权结果显式交给 DAO；不改变 EntDao 旧范围代理。
     */
    @Bean
    @ConditionalOnBean({EntityDaoFactory.class, BusinessAuthorizationService.class})
    @ConditionalOnMissingBean
    public EntityDaoAuthorizationProvider entityDaoAuthorizationProvider(
        EntityDaoFactory entityDaoFactory,
        BusinessAuthorizationService authorization
    ) {
        return new EntityDaoAuthorizationProvider(authorization, entityDaoFactory);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public RelationFactService relationFactService(ObjectProvider<RelationFactResolver> resolver,
        ConfiguredAccessPolicies configured) {
        return new RelationFactService(configured.relations(resolver.getIfAvailable()), 256);
    }

    @Bean
    @ConditionalOnBean({EntityMetaRegistry.class, GuardedSqlExecutor.class, SqlSecurityGuard.class,
        JdbcDialect.class, CrudGovernanceAuditRecorder.class})
    @ConditionalOnProperty(name = "ent.loom.crud.query.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(AuthorizedQueryExecutor.class)
    public JdbcAuthorizedQueryExecutor jdbcAuthorizedQueryExecutor(
        EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor, SqlSecurityGuard sqlGuard, JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit
    ) {
        return new JdbcAuthorizedQueryExecutor(entities, authorization, sqlExecutor, sqlGuard, dialect, audit);
    }

    @Bean
    @ConditionalOnBean({EntityMetaRegistry.class, GuardedSqlExecutor.class, SqlSecurityGuard.class,
        JdbcDialect.class, CrudGovernanceAuditRecorder.class, CrudWriteTransactionExecutor.class})
    @ConditionalOnProperty(name = "ent.loom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(AuthorizedCommandExecutor.class)
    public JdbcAuthorizedCommandExecutor jdbcAuthorizedCommandExecutor(
        EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor, SqlSecurityGuard sqlGuard, JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit, CrudWriteTransactionExecutor transactions
    ) {
        return new JdbcAuthorizedCommandExecutor(entities, authorization, sqlExecutor, sqlGuard,
            dialect, audit, transactions);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    @ConditionalOnMissingBean
    public EntityPolicyRegistry entityPolicyRegistry(
        EntityMetaRegistry entities,
        BusinessOperationCatalog operations,
        BusinessIdentityRegistry identities,
        ObjectProvider<EntityPolicyTemplate> templates,
        ObjectProvider<EntityPolicyBinding> javaBindings,
        ConfiguredAccessPolicies configured
    ) {
        Collection<EntityMeta> metas = entities.getEntityMetas();
        List<EntityPolicyBinding> bindings = new ArrayList<EntityPolicyBinding>();
        bindings.addAll(javaBindings.orderedStream().collect(Collectors.toList()));
        bindings.addAll(configured.bindings());
        List<EntityPolicyTemplate> all = new ArrayList<>(templates.orderedStream().collect(Collectors.toList()));
        all.addAll(configured.templates());
        return new EntityPolicyRegistry(metas, all, bindings, operations, identities);
    }

}
