package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
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
        DdlExecutionRequest request = new DdlExecutionRequest(
                options.getSchema(),
                options.isCreateDatabaseIfMissing(),
                options.getMode(),
                metadataLoader.load(new MetadataLoadRequest(options.getBasePackages(), options.getEntityClasses())));
        ddlEngine.execute(request, queryStrategy, sqlExecutor);
    }
}
