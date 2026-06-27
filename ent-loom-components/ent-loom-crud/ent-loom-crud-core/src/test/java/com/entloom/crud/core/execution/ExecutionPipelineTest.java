package com.entloom.crud.core.execution;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExecutionPipelineTest {
    @Test
    void execute_should_record_allow_after_success() {
        RecordingGovernanceService governanceService = new RecordingGovernanceService();
        ExecutionPipeline pipeline = new ExecutionPipeline(governanceService);

        String result = pipeline.execute(
            () -> querySpec(),
            governanceService::governQuery,
            (requestSpec, effectiveSpec, governance) -> "ok"
        );

        Assertions.assertEquals("ok", result);
        Assertions.assertEquals(1, governanceService.allowCount.get());
        Assertions.assertEquals(0, governanceService.failureCount.get());
    }

    @Test
    void execute_should_record_failure_when_executor_throws() {
        RecordingGovernanceService governanceService = new RecordingGovernanceService();
        ExecutionPipeline pipeline = new ExecutionPipeline(governanceService);
        RuntimeException expected = new IllegalStateException("boom");

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> pipeline.execute(
            () -> querySpec(),
            governanceService::governQuery,
            (requestSpec, effectiveSpec, governance) -> {
                throw expected;
            }
        ));

        Assertions.assertSame(expected, actual);
        Assertions.assertEquals(0, governanceService.allowCount.get());
        Assertions.assertEquals(1, governanceService.failureCount.get());
        Assertions.assertSame(expected, governanceService.lastFailure);
    }

    private QuerySpec<Object> querySpec() {
        return QuerySpec.<Object>builder()
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.LIST)
            .build();
    }

    private static final class RecordingGovernanceService implements CrudGovernanceService {
        private final AtomicInteger allowCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private Throwable lastFailure;

        @Override
        public <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec) {
            QueryExecutionSpec<R> effectiveSpec = QueryExecutionSpec.from(spec);
            return new CrudGovernanceResult<QueryExecutionSpec<R>>(
                subject(),
                new CrudResourceAction("Object", spec.getOp().name(), spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effectiveSpec
            );
        }

        @Override
        public <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec) {
            CommandExecutionSpec<P> effectiveSpec = CommandExecutionSpec.from(spec);
            return new CrudGovernanceResult<CommandExecutionSpec<P>>(
                subject(),
                new CrudResourceAction("Object", spec.getOp().name(), spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effectiveSpec
            );
        }

        @Override
        public <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec) {
            S effectiveSpec = spec.withGovernance(AccessDecision.ALLOW, CrudDataScope.allowAll(), CrudDataScope.allowAll());
            return new CrudGovernanceResult<S>(
                subject(),
                new CrudResourceAction("Object", "STATS", spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effectiveSpec
            );
        }

        @Override
        public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
            ImportSpec effectiveSpec = spec.withGovernance(AccessDecision.ALLOW, CrudDataScope.allowAll(), CrudDataScope.allowAll());
            return new CrudGovernanceResult<ImportSpec>(
                subject(),
                new CrudResourceAction("Object", spec.getOperation().name(), spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effectiveSpec
            );
        }

        @Override
        public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
            ExportSpec effectiveSpec = spec.withGovernance(AccessDecision.ALLOW, CrudDataScope.allowAll(), CrudDataScope.allowAll());
            return new CrudGovernanceResult<ExportSpec>(
                subject(),
                new CrudResourceAction("Object", spec.getOperation().name(), spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effectiveSpec
            );
        }

        @Override
        public void recordAllow(CrudGovernanceResult<?> result) {
            allowCount.incrementAndGet();
        }

        @Override
        public void recordExecutionFailure(CrudGovernanceResult<?> result, Throwable throwable) {
            failureCount.incrementAndGet();
            lastFailure = throwable;
        }

        private SubjectContext subject() {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId("tester");
            return subject;
        }
    }
}
