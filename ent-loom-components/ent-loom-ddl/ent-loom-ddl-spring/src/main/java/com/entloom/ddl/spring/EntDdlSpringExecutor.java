package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Spring 容器刷新后触发 DDL 执行。
 */
public final class EntDdlSpringExecutor implements ApplicationListener<ContextRefreshedEvent> {
    private final DdlEngine ddlEngine;
    private final MetadataLoader metadataLoader;
    private final QueryStrategy queryStrategy;
    private final SqlExecutor sqlExecutor;
    private final EntDdlSpringOptions options;
    private final AtomicBoolean executed = new AtomicBoolean(false);

    public EntDdlSpringExecutor(DdlEngine ddlEngine,
                                MetadataLoader metadataLoader,
                                QueryStrategy queryStrategy,
                                SqlExecutor sqlExecutor,
                                EntDdlSpringOptions options) {
        this.ddlEngine = ddlEngine;
        this.metadataLoader = metadataLoader;
        this.queryStrategy = queryStrategy;
        this.sqlExecutor = sqlExecutor;
        this.options = options;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!executed.compareAndSet(false, true)) {
            return;
        }
        if (options == null || !options.isEnabled()) {
            return;
        }
        List<DdlEntityMetadata> metadata = metadataLoader.load(
                new MetadataLoadRequest(options.getBasePackages(), options.getEntityClasses()));
        if (metadata.isEmpty() && hasConfiguredEntitySource(options)) {
            throw new IllegalStateException("已配置 DDL 实体类或扫描包，但未加载到任何 @EntDbEntity 实体，请检查 "
                    + "entloom.ddl.entity-class-names / entloom.ddl.base-packages 配置");
        }
        DdlExecutionRequest request = new DdlExecutionRequest(
                options.getSchema(),
                options.isCreateDatabaseIfMissing(),
                options.getMode(),
                metadata);
        ddlEngine.execute(request, queryStrategy, sqlExecutor);
    }

    private boolean hasConfiguredEntitySource(EntDdlSpringOptions options) {
        if (options.getEntityClasses() != null && !options.getEntityClasses().isEmpty()) {
            return true;
        }
        if (options.getBasePackages() == null || options.getBasePackages().isEmpty()) {
            return false;
        }
        for (String basePackage : options.getBasePackages()) {
            if (basePackage != null && !basePackage.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
