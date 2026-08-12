package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.service.DefaultCrudGovernanceService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.capability.command.handler.CommandHandler;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.idempotency.InMemoryIdempotencyStore;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.router.CommandRoute;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommandGatewayIdempotencyTest {
    @Test
    void should_replay_command_result_when_same_idempotency_key_and_payload_repeats() {
        AtomicInteger handlerCalls = new AtomicInteger();
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        CommandGateway gateway = commandGateway(handlerCalls, auditRecorder);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("orderNo", "ORD-1");
        CommandSpec<Map<String, Object>> spec = CommandSpec.<Map<String, Object>>builder()
            .rootType(Object.class)
            .op(CommandOperation.CREATE)
            .scene("order.create")
            .resultType(CommandResult.class)
            .payload(payload)
            .idempotencyKey("idem-1")
            .build();

        CommandResult<Integer> first = gateway.action(spec);
        CommandResult<Integer> replay = gateway.action(spec);

        Assertions.assertEquals(1, handlerCalls.get());
        Assertions.assertEquals(Integer.valueOf(1), first.getData());
        Assertions.assertFalse(first.isIdempotentReplay());
        Assertions.assertEquals(Integer.valueOf(1), replay.getData());
        Assertions.assertTrue(replay.isIdempotentReplay());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertTrue(auditRecorder.lastEvent.isSuccess());
    }

    @Test
    void should_bypass_idempotency_when_key_is_missing() {
        AtomicInteger handlerCalls = new AtomicInteger();
        CommandGateway gateway = commandGateway(handlerCalls, new RecordingAuditRecorder());
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(Object.class)
            .op(CommandOperation.CREATE)
            .scene("order.create")
            .resultType(CommandResult.class)
            .payload("payload")
            .build();

        CommandResult<Integer> first = gateway.action(spec);
        CommandResult<Integer> second = gateway.action(spec);

        Assertions.assertEquals(2, handlerCalls.get());
        Assertions.assertEquals(Integer.valueOf(1), first.getData());
        Assertions.assertEquals(Integer.valueOf(2), second.getData());
        Assertions.assertFalse(second.isIdempotentReplay());
    }

    private CommandGateway commandGateway(AtomicInteger handlerCalls, RecordingAuditRecorder auditRecorder) {
        return new CommandGatewayImpl(
            new CountingCommandRouter(handlerCalls),
            new IdempotencyManager(new InMemoryIdempotencyStore()),
            new IdempotencyPolicy(),
            new ExecutionPipeline(new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject()),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                auditRecorder
            ))
        );
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

    private SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("tenant-a");
        return subject;
    }

    private static final class CountingCommandRouter implements CommandRouter {
        private final AtomicInteger handlerCalls;

        private CountingCommandRouter(AtomicInteger handlerCalls) {
            this.handlerCalls = handlerCalls;
        }

        @Override
        public <P, R> CommandRoute<P, R> route(CommandSpec<P> spec) {
            return new CommandRoute<P, R>() {
                @Override
                public CommandHandler<P, R> handler() {
                    return new CommandHandler<P, R>() {
                        @Override
                        public boolean supports(CommandSpec<P> current) {
                            return true;
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public R action(CommandSpec<P> current) {
                            return (R) CommandResult.success(handlerCalls.incrementAndGet());
                        }
                    };
                }
            };
        }
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
