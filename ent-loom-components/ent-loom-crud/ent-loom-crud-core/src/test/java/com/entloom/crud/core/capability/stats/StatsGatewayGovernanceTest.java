package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.governance.service.DefaultCrudGovernanceService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.core.capability.stats.StatsQueryEngine;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatsGatewayGovernanceTest {
    @Test
    void stats_gateway_should_resolve_subject_and_record_allow_audit() {
        SubjectContext resolved = subject("resolved-user");
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        StatsGateway gateway = statsGateway(
            statsQueryEngine(),
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(resolved),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                auditRecorder
            )
        );

        StatsSpec.Builder specBuilder = StatsSpec.builder();
        specBuilder.scene("order.stats");
        specBuilder.rootType(Object.class);
        specBuilder.payload(new StatsQueryPayload());
        StatsSpec spec = specBuilder.build();

        gateway.stats(spec);

        Assertions.assertNull(spec.getSubject());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertEquals("resolved-user", auditRecorder.lastEvent.getSubject().getSubjectId());
        Assertions.assertEquals("tenant-a", auditRecorder.lastEvent.getSubject().getTenantId());
        Assertions.assertTrue(auditRecorder.lastEvent.isAllowed());
        Assertions.assertTrue(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals("STATS:QUERY", auditRecorder.lastEvent.getAction().getAction());
        Assertions.assertEquals("STATS", auditRecorder.lastEvent.getAction().getOperationDomain().name());
        Assertions.assertEquals("QUERY", auditRecorder.lastEvent.getAction().getOperation());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.NONE, auditRecorder.lastEvent.getReason());
    }

    @Test
    void stats_gateway_should_record_execution_failure_audit() {
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        RuntimeException expected = new IllegalStateException("stats execution failed");
        StatsGateway gateway = statsGateway(
            new StatsQueryEngine() {
                @Override
                public StatsResult stats(StatsSpec spec) {
                    throw expected;
                }
            },
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                auditRecorder
            )
        );

        StatsSpec.Builder specBuilder = StatsSpec.builder();
        specBuilder.scene("order.stats");
        specBuilder.rootType(Object.class);
        specBuilder.payload(new StatsQueryPayload());
        StatsSpec spec = specBuilder.build();

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> gateway.stats(spec));
        Assertions.assertSame(expected, actual);
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertTrue(auditRecorder.lastEvent.isAllowed());
        Assertions.assertFalse(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.EXECUTION_FAILED, auditRecorder.lastEvent.getReason());
    }

    @Test
    void stats_gateway_should_preserve_stats_fields_and_server_attributes_after_governance() {
        AtomicReference<StatsSpec> engineSpecRef = new AtomicReference<StatsSpec>();
        StatsGateway gateway = statsGateway(
            new StatsQueryEngine() {
                @Override
                public StatsResult stats(StatsSpec spec) {
                    engineSpecRef.set(spec);
                    return null;
                }
            },
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                new RecordingAuditRecorder(),
                new DefaultCrudSpecAttributeResolver(Collections.singletonList(spec -> {
                    Map<String, Object> attrs = new LinkedHashMap<String, Object>();
                    attrs.put("traceId", "stats-trace");
                    return attrs;
                }))
            )
        );

        StatsQueryPayload payload = new StatsQueryPayload();
        payload.setIncludeSummary(Boolean.TRUE);
        payload.setGroupBy(Arrays.asList(new StatsGroupBy("orgId")));
        StatsSpec.Builder builder = StatsSpec.builder();
        builder.scene("order.stats");
        builder.rootType(Object.class);
        builder.payload(payload);
        builder.mode(StatsQueryMode.LIST);
        builder.includeExecutionMeta(true);
        StatsSpec spec = builder.build();

        gateway.stats(spec);

        StatsSpec engineSpec = engineSpecRef.get();
        Assertions.assertNotNull(engineSpec);
        Assertions.assertFalse(QuerySpec.class.isAssignableFrom(engineSpec.getClass()));
        Assertions.assertEquals("stats-trace", engineSpec.getAttributes().get("traceId"));
        Assertions.assertEquals(StatsQueryMode.LIST, engineSpec.getMode());
        Assertions.assertTrue(engineSpec.isIncludeExecutionMeta());
        Assertions.assertEquals(Boolean.TRUE, engineSpec.getPayload().getIncludeSummary());
        Assertions.assertEquals("orgId", engineSpec.getPayload().getGroupBy().get(0).getField());
        Assertions.assertNull(spec.getAttributes().get("traceId"));
    }

    private StatsQueryEngine statsQueryEngine() {
        return new StatsQueryEngine() {
            @Override
            public StatsResult stats(StatsSpec spec) {
                return null;
            }
        };
    }

    private StatsGateway statsGateway(StatsQueryEngine statsQueryEngine, CrudGovernanceService governanceService) {
        return new StatsGatewayImpl(statsQueryEngine, new ExecutionPipeline(governanceService));
    }

    private EntityMetaRegistry testMetaRegistry() {
        final ResourceDescriptor descriptor = new ResourceDescriptor(
            Object.class,
            "Object",
            "test-service",
            Collections.<String>emptyList()
        );
        final EntityMeta meta = new EntityMeta(
            Object.class,
            descriptor,
            "t_object",
            "id",
            null,
            Collections.emptyMap()
        );
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return meta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return descriptor;
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return RelationGraph.empty();
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    private CrudPermissionService allowPermission() {
        return new CrudPermissionService() {
            @Override
            public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
                return AccessDecision.ALLOW;
            }
        };
    }

    private CrudDataScopeResolver allowScope() {
        return new CrudDataScopeResolver() {
            @Override
            public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
                return CrudDataScope.allowAll();
            }

            @Override
            public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
                return CrudDataScope.allowAll();
            }
        };
    }

    private SubjectContext subject(String subjectId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        subject.setTenantId("tenant-a");
        return subject;
    }

    private static final class FixedSubjectResolver implements CrudSubjectResolver {
        private final SubjectContext subject;

        private FixedSubjectResolver(SubjectContext subject) {
            this.subject = subject;
        }

        @Override
        public SubjectContext resolveOrThrow() {
            return subject;
        }
    }

    private static final class RecordingAuditRecorder implements CrudGovernanceAuditRecorder {
        private CrudGovernanceAuditEvent lastEvent;

        @Override
        public void record(CrudGovernanceAuditEvent event) {
            this.lastEvent = event;
        }
    }
}
