package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.adapter.AccessEntryResolver;
import com.entloom.crud.core.adapter.AttributeAccessEntryResolver;
import com.entloom.crud.core.adapter.ContextAccessEntryAttributeContributor;
import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeRelationModel;
import com.entloom.crud.core.governance.audit.CompositeCrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.audit.LoggingCrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.permission.CrudPermissionRule;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.permission.RuleBasedCrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.DefaultCrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.governance.subject.FailClosedCrudSubjectResolver;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeContributor;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.spec.CrudSpecReservedAttributeKeyProvider;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialectResolver;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.governance.JdbcCrudGovernanceAuditRecorder;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.log.SqlLogLevel;
import com.entloom.crud.spring.config.CrudProperties;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 核心公共能力装配（元数据、校验、日志与基础 JDBC）。
 */
@Configuration
public class CrudCommonConfiguration {
    /**
     * 构建命令幂等校验策略，支持业务覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyPolicy idempotencyPolicy(CrudProperties properties) {
        CrudProperties.Idempotency config = properties.getIdempotency();
        return new IdempotencyPolicy(config.getMode(), config.getRequiredOps(), config.getRequiredScenes());
    }

    /**
     * 统一的 Spec 校验器，确保 Query/Command 入口规则一致。
     */
    @Bean
    @ConditionalOnMissingBean
    public SpecValidator specValidator(IdempotencyPolicy idempotencyPolicy) {
        return new SpecValidator(idempotencyPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudSubjectResolver crudSubjectResolver() {
        return new FailClosedCrudSubjectResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessEntryResolver accessEntryResolver() {
        return new AttributeAccessEntryResolver();
    }

    @Bean
    @ConditionalOnMissingBean(name = "crudAccessEntryAttributeContributor")
    public ContextAccessEntryAttributeContributor crudAccessEntryAttributeContributor() {
        return new ContextAccessEntryAttributeContributor();
    }

    /**
     * 创建 CRUD 权限服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public CrudPermissionService crudPermissionService(CrudProperties properties) {
        List<CrudPermissionRule> rules = new ArrayList<CrudPermissionRule>();
        for (CrudProperties.PermissionRule rule : properties.getGovernance().getPermissionRules()) {
            rules.add(new CrudPermissionRule(
                rule.getResource(),
                rule.getAction(),
                rule.getScene(),
                rule.getDecision(),
                rule.getSubjectIds(),
                rule.getTenantIds(),
                rule.getOrgIds()
            ));
        }
        return new RuleBasedCrudPermissionService(rules);
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudDataScopeResolver crudDataScopeResolver() {
        return new DefaultCrudDataScopeResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudSpecAttributeResolver crudSpecAttributeResolver(
        ObjectProvider<CrudSpecAttributeContributor[]> contributors,
        ObjectProvider<CrudSpecReservedAttributeKeyProvider[]> reservedKeyProviders
    ) {
        List<CrudSpecAttributeContributor> orderedContributors = new ArrayList<CrudSpecAttributeContributor>();
        CrudSpecAttributeContributor[] values = contributors.getIfAvailable();
        if (values != null) {
            for (CrudSpecAttributeContributor contributor : values) {
                if (contributor != null) {
                    orderedContributors.add(contributor);
                }
            }
        }
        AnnotationAwareOrderComparator.sort(orderedContributors);
        return new DefaultCrudSpecAttributeResolver(orderedContributors, collectReservedAttributeKeys(reservedKeyProviders));
    }

    private List<String> collectReservedAttributeKeys(
        ObjectProvider<CrudSpecReservedAttributeKeyProvider[]> reservedKeyProviders
    ) {
        List<String> reservedKeys = new ArrayList<String>();
        CrudSpecReservedAttributeKeyProvider[] providers = reservedKeyProviders.getIfAvailable();
        if (providers == null) {
            return reservedKeys;
        }
        for (CrudSpecReservedAttributeKeyProvider provider : providers) {
            if (provider == null || provider.reservedAttributeKeys() == null) {
                continue;
            }
            for (String key : provider.reservedAttributeKeys()) {
                if (key != null && !key.trim().isEmpty()) {
                    reservedKeys.add(key);
                }
            }
        }
        return reservedKeys;
    }

    @Bean
    @ConditionalOnMissingBean
    public CrudGovernanceAuditRecorder crudGovernanceAuditRecorder(
        CrudProperties properties,
        ObjectProvider<JdbcTemplate> jdbcTemplateProvider
    ) {
        List<CrudGovernanceAuditRecorder> delegates = new ArrayList<CrudGovernanceAuditRecorder>();
        delegates.add(new LoggingCrudGovernanceAuditRecorder());
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null && properties.getGovernance().getAudit().isPersistToJdbc()) {
            delegates.add(new JdbcCrudGovernanceAuditRecorder(
                jdbcTemplate,
                properties.getGovernance().getAudit().getTableName(),
                properties.getGovernance().getAudit().isAutoInitializeSchema()
            ));
        }
        if (delegates.size() == 1) {
            return delegates.get(0);
        }
        return new CompositeCrudGovernanceAuditRecorder(delegates);
    }

    /**
     * 实体元数据注册器。
     */
    @Bean
    @ConditionalOnMissingBean
    public EntityMetaRegistry metaRegistry(
        ObjectProvider<ResourceCatalogAdapter[]> resourceCatalogAdapters
    ) {
        ResourceCatalogAdapter[] adapters = resourceCatalogAdapters.getIfAvailable();
        if (adapters != null && adapters.length > 0) {
            List<ResourceCatalogAdapter> orderedAdapters = new ArrayList<ResourceCatalogAdapter>();
            for (ResourceCatalogAdapter adapter : adapters) {
                if (adapter != null) {
                    orderedAdapters.add(adapter);
                }
            }
            AnnotationAwareOrderComparator.sort(orderedAdapters);
            if (!orderedAdapters.isEmpty()) {
                return new CrudRuntimeModelBackedEntityMetaRegistry(mergeRuntimeModels(orderedAdapters));
            }
        }
        throw new ValidationException("未找到 ResourceCatalogAdapter，CRUD 运行期必须由 CrudRuntimeModel 构建 EntityMetaRegistry");
    }

    private CrudRuntimeModel mergeRuntimeModels(List<ResourceCatalogAdapter> orderedAdapters) {
        List<CrudRuntimeEntityModel> entities = new ArrayList<CrudRuntimeEntityModel>();
        List<CrudRuntimeRelationModel> relations = new ArrayList<CrudRuntimeRelationModel>();
        for (ResourceCatalogAdapter adapter : orderedAdapters) {
            CrudRuntimeModel model = adapter.runtimeModel();
            if (model == null) {
                continue;
            }
            entities.addAll(model.getEntities().values());
            relations.addAll(model.getRelations());
        }
        return new CrudRuntimeModel(entities, relations);
    }

    /**
     * SQL 执行日志器，支持 SAFE/FULL 两种输出模式。
     */
    /**
     * 创建 SQL 执行日志器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlExecutionLogger sqlExecutionLogger(CrudProperties properties) {
        SqlExecutionLogger logger = new SqlExecutionLogger();
        CrudProperties.SqlLog sqlLog = properties.getSqlLog();
        CrudProperties.SqlLog.Mode mode = sqlLog.getMode();
        logger.setSampleRate(sqlLog.getSampleRate());
        logger.setOutput(SqlExecutionLogger.Output.valueOf(sqlLog.getOutput().name()));
        logger.setPretty(sqlLog.isPretty());
        if (mode == CrudProperties.SqlLog.Mode.FULL) {
            logger.setMode(SqlLogLevel.FULL);
        } else {
            logger.setMode(SqlLogLevel.SAFE);
        }
        return logger;
    }

    /**
     * 在仅有 DataSource 的场景下补齐 JdbcTemplate，降低接入门槛。
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 统一 JDBC 方言解析，优先基于实际数据源自动识别。
     */
    @Bean
    @ConditionalOnMissingBean
    public JdbcDialect jdbcDialect(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate == null ? StandardJdbcDialect.GENERIC : JdbcDialectResolver.resolve(jdbcTemplate);
    }

}
