package com.entloom.ddl.bootstrap;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.core.DefaultDdlEngine;

/**
 * 无 Spring 场景下的 DDL 启动入口。
 *
 * <p>显式实体类和实体包统一通过 {@link DdlBootstrapRequest} 进入发现流程。
 * 每次调用都会重新发现并按类名稳定排序；重复调用不会复用或追加上一次调用的状态。</p>
 */
public final class DdlBootstrap {
    private final DdlEngine ddlEngine;
    private final MetadataLoader metadataLoader;
    private final QueryStrategy queryStrategy;
    private final SqlExecutor sqlExecutor;

    public DdlBootstrap() {
        this(new DefaultDdlEngine(), new AnnotationMetadataLoader(new ClasspathEntityClassResolver(null)), null, null);
    }

    public DdlBootstrap(DdlEngine ddlEngine,
                        MetadataLoader metadataLoader,
                        QueryStrategy queryStrategy,
                        SqlExecutor sqlExecutor) {
        this.ddlEngine = ddlEngine == null ? new DefaultDdlEngine() : ddlEngine;
        this.metadataLoader = metadataLoader == null ? new AnnotationMetadataLoader() : metadataLoader;
        this.queryStrategy = queryStrategy;
        this.sqlExecutor = sqlExecutor;
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
