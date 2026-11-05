package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.capability.command.engine.AuthorizedCommandExecutor;
import com.entloom.crud.core.capability.command.engine.AuthorizedBatchUpdateExecutor;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.validation.EntityWriteValidator;
import com.entloom.crud.starter.command.SpringEntityWriteValidator;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionEvaluator;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.fact.RelationFactResolver;
import com.entloom.crud.core.governance.fact.RelationFactService;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.QueryDefaultSortResolver;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.query.JdbcAuthorizedQueryExecutor;
import com.entloom.crud.engine.jdbc.command.JdbcAuthorizedCommandExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** 汇总 YAML、Java 声明与实体注解，在启动期交由同一 Core 编译器校验。 */
@AutoConfiguration(after = CrudAutoConfiguration.class, before = CrudAccessWebAutoConfiguration.class)
@Import({CrudPropertiesConfiguration.class, CrudIdentityConfiguration.class, CrudEntityDeclarationConfiguration.class})
public class CrudPolicyAutoConfiguration {
    /** 注册器完成后检查真实 Handler 与已启用执行器，避免配置声明被误当成执行能力。 */
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    public org.springframework.beans.factory.SmartInitializingSingleton configuredAccessExecutionGuard(
        ConfiguredAccessPolicies configured, EntityMetaRegistry entities,
        ObjectProvider<com.entloom.crud.core.runtime.router.CommandRouter> routers,
        ObjectProvider<AuthorizedCommandExecutor> commands, ObjectProvider<AuthorizedQueryExecutor> queries) {
        return () -> {
            for (var mapping : configured.mappings()) {
                var operation = mapping.getOperationKey();
                String resource = mapping.getBusinessOperation().getResourceCode();
                if (operation.getDomain() == com.entloom.crud.api.enums.CrudOperationDomain.QUERY && queries.getIfAvailable() == null)
                    throw new ValidationException("实体 " + resource + ": 查询动作缺少已启用执行器，来源: " + mapping.getSource());
                if (operation.getDomain() != com.entloom.crud.api.enums.CrudOperationDomain.COMMAND) continue;
                if (commands.getIfAvailable() == null) throw new ValidationException("实体 " + resource + ": 写入动作缺少已启用执行器，来源: " + mapping.getSource());
                if (!"ACTION".equals(operation.getOperation())) continue;
                var meta = entities.getEntityMetas().stream().filter(entity ->
                    entity.getResourceDescriptor().getResourceCode().equals(resource)).findFirst().orElseThrow();
                var router = routers.getIfAvailable();
                if (!(router instanceof com.entloom.crud.core.runtime.router.CommandActionSceneResolver))
                    throw new ValidationException("实体 " + resource + ": 路由器不支持业务 Handler 契约校验");
                try {
                    var contract = ((com.entloom.crud.core.runtime.router.CommandActionSceneResolver) router).resolveActionContract(
                        meta.getEntityType(), List.of(meta.getEntityType()), mapping.getScene());
                    for (var access : contract.getRelatedAccess()) {
                        var target = entities.getEntityMeta(access.getEntityType());
                        var field = target == null ? null : target.resolveFieldMeta(access.getScopeField());
                        if (target == null || field == null || field.isRelation() || target.getEntityType() == meta.getEntityType())
                            throw new ValidationException("关联 DAO 实体或范围字段无效: " + access.getEntityType().getName() + "." + access.getScopeField());
                    }
                } catch (RuntimeException failure) {
                    throw new ValidationException("实体 " + resource + ": 业务 Handler 缺失或关联 DAO 声明无效: " + mapping.getScene() + "，来源: " + mapping.getSource());
                }
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntityMetaRegistry.class)
    public ScenePolicyRegistry scenePolicyRegistry(EntityMetaRegistry entities, CrudEntityDeclarations declarations,
        ObjectProvider<com.entloom.crud.core.governance.policy.ScenePolicy> policies,
        org.springframework.beans.factory.ListableBeanFactory beans) {
        declarations.validate(entities);
        var all = new ArrayList<com.entloom.crud.core.governance.policy.ScenePolicy>();
        all.addAll(CrudActionPolicies.collect(beans, entities));
        policies.orderedStream().forEach(all::add);
        return ScenePolicyRegistry.create(entities, all, declarations.actions(), java.util.Collections.emptyMap());
    }

    @Bean
    @ConditionalOnMissingBean(EntityWriteValidator.class)
    public EntityWriteValidator entityWriteValidator(ObjectProvider<org.springframework.validation.Validator> validators) {
        return new SpringEntityWriteValidator(validators);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    public ConfiguredAccessPolicies configuredAccessPolicies(CrudAccessProperties properties,
        EntityMetaRegistry entities, BusinessIdentityRegistry identities, ConfiguredBusinessIdentities definitions,
        CrudEntityDeclarations declarations, ObjectProvider<EntityPolicyTemplate> templates,
        ObjectProvider<EntityPolicyBinding> javaBindings, ScenePolicyRegistry scenes) {
        // 在生成操作目录前检查所有来源，重名始终报告策略冲突，不受 Bean 装配顺序影响。
        var sources = new LinkedHashMap<String, String>();
        properties.getPolicies().keySet().forEach(code -> sources.put(code, "ent.loom.crud.access.policies." + code));
        templates.orderedStream().forEach(template -> {
            String previous = sources.putIfAbsent(template.getCode(), template.getSource());
            if (previous != null) {
                throw new ValidationException("策略模板重复: " + template.getCode()
                    + "，来源: " + previous + " / " + template.getSource());
            }
        });
        return new ConfiguredAccessPolicies(properties, entities, identities, definitions, declarations,
            javaBindings.orderedStream().map(EntityPolicyBinding::getEntityType).collect(Collectors.toSet()), scenes);
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
        ConfiguredAccessPolicies configured,
        ScenePolicyRegistry scenes
    ) {
        List<CrudOperationMapping> all = new ArrayList<>(mappings.orderedStream().collect(Collectors.toList()));
        all.addAll(configured.mappings());
        return new CrudOperationMappingRegistry(entities.getEntityMetas(), operations, all, scenes);
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
        CrudGovernanceAuditRecorder audit, QueryDefaultSortResolver defaultSortResolver
    ) {
        return new JdbcAuthorizedQueryExecutor(entities, authorization, sqlExecutor, sqlGuard, dialect, audit, defaultSortResolver);
    }

    @Bean
    @ConditionalOnBean({EntityMetaRegistry.class, GuardedSqlExecutor.class, SqlSecurityGuard.class,
        JdbcDialect.class, CrudGovernanceAuditRecorder.class, CrudWriteTransactionExecutor.class})
    @ConditionalOnProperty(name = "ent.loom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(AuthorizedCommandExecutor.class)
    public AuthorizedCommandExecutor jdbcAuthorizedCommandExecutor(
        EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor, SqlSecurityGuard sqlGuard, JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit, CrudWriteTransactionExecutor transactions,
        ObjectProvider<EntityDaoFactory> daos, ObjectProvider<CrudInputContract> contracts,
        EntityWriteValidator validator, com.entloom.crud.core.runtime.router.CommandRouter router,
        ScenePolicyRegistry scenes
    ) {
        EntityDaoFactory factory = daos.getIfAvailable();
        AuthorizedBatchUpdateExecutor batchUpdates = factory == null ? null : new AuthorizedBatchUpdateExecutor(
            entities, authorization, factory, sqlGuard, audit, contracts.getIfAvailable(CrudInputContract::empty), validator);
        var jdbc = new JdbcAuthorizedCommandExecutor(entities, authorization, sqlExecutor, sqlGuard,
            dialect, audit, transactions, batchUpdates, validator, contracts.getIfAvailable(CrudInputContract::empty));
        return new com.entloom.crud.core.capability.command.engine.AuthorizedActionCommandExecutor(
            jdbc, authorization, router, entities, transactions, audit, scenes);
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
        List<EntityPolicyBinding> bindings = new ArrayList<EntityPolicyBinding>();
        bindings.addAll(javaBindings.orderedStream().collect(Collectors.toList()));
        bindings.addAll(configured.bindings());
        List<EntityPolicyTemplate> all = new ArrayList<>(templates.orderedStream().collect(Collectors.toList()));
        all.addAll(configured.templates());
        return new EntityPolicyRegistry(entities, all, bindings, operations, identities);
    }

    @Bean
    @ConditionalOnBean(EntityMetaRegistry.class)
    public org.springframework.beans.factory.SmartInitializingSingleton crudPageFieldLimitValidator(
        EntityMetaRegistry entities, EntityPolicyRegistry policies, CrudOperationMappingRegistry mappings) {
        return () -> policies.validatePageMappings(mappings, entities.getEntityMetas());
    }
}
