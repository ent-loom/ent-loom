package com.entloom.ddl.bootstrap;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.core.DefaultDdlEngine;
import com.entloom.ddl.core.NoopQueryStrategy;
import com.entloom.ddl.core.NoopSqlExecutor;

/**
 * 无 Spring 场景下的 DDL 启动入口。
 */
public final class DdlBootstrap {
    private final DdlEngine ddlEngine;
    private final MetadataLoader metadataLoader;
    private final QueryStrategy queryStrategy;
    private final SqlExecutor sqlExecutor;

    public DdlBootstrap() {
        this(new DefaultDdlEngine(), new AnnotationMetadataLoader(), new NoopQueryStrategy(), new NoopSqlExecutor());
    }

    public DdlBootstrap(DdlEngine ddlEngine,
                        MetadataLoader metadataLoader,
                        QueryStrategy queryStrategy,
                        SqlExecutor sqlExecutor) {
        this.ddlEngine = ddlEngine == null ? new DefaultDdlEngine() : ddlEngine;
        this.metadataLoader = metadataLoader == null ? new AnnotationMetadataLoader() : metadataLoader;
        this.queryStrategy = queryStrategy == null ? new NoopQueryStrategy() : queryStrategy;
        this.sqlExecutor = sqlExecutor == null ? new NoopSqlExecutor() : sqlExecutor;
    }

    public DdlExecutionResult execute(DdlBootstrapRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        MetadataLoadRequest metadataLoadRequest = new MetadataLoadRequest(request.basePackages(), request.entityClasses());
        DdlExecutionRequest executionRequest = new DdlExecutionRequest(
                request.schema(),
                request.createDatabaseIfMissing(),
                request.mode(),
                metadataLoader.load(metadataLoadRequest));
        return ddlEngine.execute(executionRequest, queryStrategy, sqlExecutor);
    }
}
