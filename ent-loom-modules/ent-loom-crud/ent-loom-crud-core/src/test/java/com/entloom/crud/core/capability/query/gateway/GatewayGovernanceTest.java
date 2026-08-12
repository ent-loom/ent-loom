package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
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
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.gateway.CommandGatewayImpl;
import com.entloom.crud.core.capability.command.handler.CommandHandler;
import com.entloom.crud.core.capability.query.handler.QueryHandler;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.router.CommandRoute;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.router.QueryRoute;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeKeys;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.enums.QueryStrategy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GatewayGovernanceTest {
    @Test
    void specs_should_not_expose_mutable_compat_api() {
        assertNoPublicSetter(BaseSpec.class);
        assertNoPublicSetter(QuerySpec.class);
        assertNoPublicSetter(CommandSpec.class);
        assertNoPublicSetter(QueryExecutionSpec.class);
        assertNoPublicSetter(CommandExecutionSpec.class);
        assertNoNoArgConstructor(QuerySpec.class);
        assertNoNoArgConstructor(CommandSpec.class);
        assertNoNoArgConstructor(QueryExecutionSpec.class);
        assertNoNoArgConstructor(CommandExecutionSpec.class);
    }

    private void assertNoPublicSetter(Class<?> specType) {
        for (Method method : specType.getMethods()) {
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                Assertions.fail(specType.getSimpleName() + " must not expose setter: " + method.getName());
            }
        }
    }

    private void assertNoNoArgConstructor(Class<?> specType) {
        for (Constructor<?> constructor : specType.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                Assertions.fail(specType.getSimpleName() + " must not expose no-arg constructor");
            }
        }
    }

    private QueryGateway queryGateway(QueryRouter queryRouter, CrudGovernanceService governanceService) {
        return new QueryGatewayImpl(queryRouter, new ExecutionPipeline(governanceService));
    }

    private CommandGateway commandGateway(
        CommandRouter commandRouter,
        IdempotencyManager idempotencyManager,
        IdempotencyPolicy idempotencyPolicy,
        CrudGovernanceService governanceService
    ) {
        return new CommandGatewayImpl(
            commandRouter,
            idempotencyManager,
            idempotencyPolicy,
            new ExecutionPipeline(governanceService)
        );
    }

    private EntityMetaRegistry testMetaRegistry() {
        return testMetaRegistry("Object");
    }

    private EntityMetaRegistry testMetaRegistry(String resourceCode) {
        final ResourceDescriptor descriptor = new ResourceDescriptor(
            Object.class,
            resourceCode,
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

    @Test
    void query_gateway_should_resolve_subject_and_record_allow_audit() {
        SubjectContext resolved = new SubjectContext();
        resolved.setSubjectId(" resolved-user ");
        resolved.setTenantId(" tenant-a ");
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        QueryGateway gateway = queryGateway(
            queryRouter(),
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

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .build();

        gateway.page(spec);

        Assertions.assertNull(spec.getSubject());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertNotSame(resolved, auditRecorder.lastEvent.getSubject());
        Assertions.assertEquals("resolved-user", auditRecorder.lastEvent.getSubject().getSubjectId());
        Assertions.assertEquals("tenant-a", auditRecorder.lastEvent.getSubject().getTenantId());
        Assertions.assertEquals(" resolved-user ", resolved.getSubjectId());
        Assertions.assertEquals(" tenant-a ", resolved.getTenantId());
        Assertions.assertTrue(auditRecorder.lastEvent.isAllowed());
        Assertions.assertTrue(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.SUCCESS, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals("QUERY:PAGE", auditRecorder.lastEvent.getAction().getAction());
        Assertions.assertEquals("QUERY", auditRecorder.lastEvent.getAction().getOperationDomain().name());
        Assertions.assertEquals("PAGE", auditRecorder.lastEvent.getAction().getOperation());
    }

    @Test
    void governance_should_build_action_from_resource_descriptor() {
        EntityMetaRegistry metaRegistry = testMetaRegistry("business-order");
        AtomicReference<CrudResourceAction> actionRef = new AtomicReference<CrudResourceAction>();
        QueryGateway gateway = queryGateway(
            queryRouter(),
            new DefaultCrudGovernanceService(
                metaRegistry,
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                new CrudPermissionService() {
                    @Override
                    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
                        actionRef.set(action);
                        return AccessDecision.ALLOW;
                    }
                },
                allowScope(),
                Collections.emptyList(),
                new RecordingAuditRecorder()
            )
        );

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .build();

        gateway.page(spec);

        Assertions.assertNotNull(actionRef.get());
        Assertions.assertEquals("business-order", actionRef.get().getResource());
        Assertions.assertEquals("QUERY:PAGE", actionRef.get().getAction());
        Assertions.assertEquals("QUERY", actionRef.get().getOperationDomain().name());
        Assertions.assertEquals("PAGE", actionRef.get().getOperation());
        Assertions.assertEquals("test-service", actionRef.get().getOwnerService());
        Assertions.assertSame(metaRegistry.getResourceDescriptor(Object.class), actionRef.get().getResourceDescriptor());
    }

    @Test
    void query_gateway_should_fail_closed_when_subject_identity_missing() {
        SubjectContext resolved = new SubjectContext();
        resolved.setOrgId("org-a");
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        QueryGateway gateway = queryGateway(
            queryRouter(),
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

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .build();

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> gateway.page(spec));
        Assertions.assertEquals("操作主体(subject.subjectId)不能为空", ex.getMessage());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertFalse(auditRecorder.lastEvent.isAllowed());
    }

    @Test
    void query_gateway_should_apply_spec_defaults_without_mutating_input_spec() {
        AtomicReference<QuerySpec<Object>> executionSpecRef = new AtomicReference<QuerySpec<Object>>();
        QueryGateway gateway = queryGateway(
            new QueryRouter() {
                @Override
                public <R> QueryRoute<R> route(QuerySpec<R> spec) {
                    return new QueryRoute<R>() {
                        @Override
                        public QueryHandler<R> handler() {
                            return new QueryHandler<R>() {
                                @Override
                                public boolean supports(QuerySpec<R> current) {
                                    return true;
                                }

                                @Override
                                public PageResult<R> page(QuerySpec<R> current) {
                                    executionSpecRef.set((QuerySpec<Object>) current);
                                    return new PageResult<R>(Collections.emptyList(), 0, 1, 10);
                                }

                                @Override
                                public List<R> list(QuerySpec<R> current) {
                                    return Collections.emptyList();
                                }

                                @Override
                                public R detail(QuerySpec<R> current) {
                                    return null;
                                }
                            };
                        }
                    };
                }
            },
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                new RecordingAuditRecorder()
            )
        );

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .page(new PageRequest(0, 0))
            .build();

        gateway.page(spec);

        QuerySpec<Object> executionSpec = executionSpecRef.get();
        Assertions.assertNotNull(executionSpec);
        Assertions.assertEquals(QueryOperation.PAGE, executionSpec.getOp());
        Assertions.assertEquals(1, executionSpec.getPage().getPage());
        Assertions.assertEquals(10, executionSpec.getPage().getLimit());
        Assertions.assertNull(spec.getOp());
        Assertions.assertNull(spec.getSubject());
        Assertions.assertEquals(0, spec.getPage().getPage());
        Assertions.assertEquals(0, spec.getPage().getLimit());
    }

    @Test
    void query_gateway_should_apply_route_default_strategy_without_mutating_input_spec() {
        AtomicReference<QuerySpec<Object>> executionSpecRef = new AtomicReference<QuerySpec<Object>>();
        QueryGateway gateway = queryGateway(
            new QueryRouter() {
                @Override
                public <R> QueryRoute<R> route(QuerySpec<R> spec) {
                    return new QueryRoute<R>() {
                        @Override
                        public QueryHandler<R> handler() {
                            return new QueryHandler<R>() {
                                @Override
                                public boolean supports(QuerySpec<R> current) {
                                    return true;
                                }

                                @Override
                                public PageResult<R> page(QuerySpec<R> current) {
                                    return new PageResult<R>(Collections.emptyList(), 0, 1, 10);
                                }

                                @Override
                                public List<R> list(QuerySpec<R> current) {
                                    executionSpecRef.set((QuerySpec<Object>) current);
                                    return Collections.emptyList();
                                }

                                @Override
                                public R detail(QuerySpec<R> current) {
                                    return null;
                                }
                            };
                        }

                        @Override
                        public QueryStrategy handlerDefaultStrategy() {
                            return QueryStrategy.ROOT_FIRST;
                        }
                    };
                }
            },
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                new RecordingAuditRecorder()
            )
        );

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.list")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.LIST)
            .strategy(QueryStrategy.DEFAULT)
            .build();

        gateway.list(spec);

        QuerySpec<Object> executionSpec = executionSpecRef.get();
        Assertions.assertNotNull(executionSpec);
        Assertions.assertNotSame(spec, executionSpec);
        Assertions.assertEquals(QueryStrategy.ROOT_FIRST, executionSpec.getHandlerDefaultStrategy());
        Assertions.assertEquals(QueryStrategy.DEFAULT, executionSpec.getStrategy());
        Assertions.assertEquals(QueryStrategy.DEFAULT, spec.getHandlerDefaultStrategy());
    }

    @Test
    void query_gateway_should_merge_server_attributes_and_strip_manual_reserved_keys() {
        AtomicReference<BaseSpec> permissionSpecRef = new AtomicReference<BaseSpec>();
        AtomicReference<QuerySpec<?>> scopeSpecRef = new AtomicReference<QuerySpec<?>>();
        AtomicReference<QuerySpec<Object>> routeSpecRef = new AtomicReference<QuerySpec<Object>>();
        AtomicReference<QuerySpec<Object>> handlerSpecRef = new AtomicReference<QuerySpec<Object>>();
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        QueryGateway gateway = queryGateway(
            new QueryRouter() {
                @Override
                public <R> QueryRoute<R> route(QuerySpec<R> spec) {
                    routeSpecRef.set((QuerySpec<Object>) spec);
                    return new QueryRoute<R>() {
                        @Override
                        public QueryHandler<R> handler() {
                            return new QueryHandler<R>() {
                                @Override
                                public boolean supports(QuerySpec<R> current) {
                                    return true;
                                }

                                @Override
                                public PageResult<R> page(QuerySpec<R> current) {
                                    handlerSpecRef.set((QuerySpec<Object>) current);
                                    return new PageResult<R>(Collections.emptyList(), 0, 1, 10);
                                }

                                @Override
                                public List<R> list(QuerySpec<R> current) {
                                    return Collections.emptyList();
                                }

                                @Override
                                public R detail(QuerySpec<R> current) {
                                    return null;
                                }
                            };
                        }
                    };
                }
            },
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                new CrudPermissionService() {
                    @Override
                    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
                        permissionSpecRef.set(spec);
                        return AccessDecision.ALLOW;
                    }
                },
                new CrudDataScopeResolver() {
                    private final CrudDataScopeResolver delegate = new com.entloom.crud.core.governance.scope.DefaultCrudDataScopeResolver();

                    @Override
                    public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
                        scopeSpecRef.set(spec);
                        return delegate.resolveQueryScope(action, subject, spec);
                    }

                    @Override
                    public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
                        return delegate.resolveCommandScope(action, subject, spec);
                    }
                },
                Collections.emptyList(),
                auditRecorder,
                new DefaultCrudSpecAttributeResolver(Collections.singletonList(spec -> {
                    Map<String, Object> attrs = new LinkedHashMap<String, Object>();
                    Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
                    dimensions.put("orgId", "server-org");
                    attrs.put("traceId", "server-trace");
                    attrs.put(CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS, dimensions);
                    return attrs;
                }))
            )
        );

        Map<String, Object> manualAttributes = new LinkedHashMap<String, Object>();
        manualAttributes.put("traceId", "client-trace");
        manualAttributes.put(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL, Boolean.TRUE);
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .attributes(manualAttributes)
            .build();

        gateway.page(spec);

        Assertions.assertEquals("client-trace", spec.getAttributes().get("traceId"));
        Assertions.assertEquals(Boolean.TRUE, spec.getAttributes().get(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL));
        Assertions.assertEquals("server-trace", permissionSpecRef.get().getAttributes().get("traceId"));
        Assertions.assertFalse(permissionSpecRef.get().getAttributes().containsKey(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL));
        Assertions.assertEquals("server-trace", scopeSpecRef.get().getAttributes().get("traceId"));
        Assertions.assertEquals("server-trace", routeSpecRef.get().getAttributes().get("traceId"));
        Assertions.assertEquals("server-trace", handlerSpecRef.get().getAttributes().get("traceId"));
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertFalse(auditRecorder.lastEvent.getGrantedScope().isExplicitAll());
        Assertions.assertEquals("server-org", auditRecorder.lastEvent.getGrantedScope().getDimensions().get("orgId"));
    }

    @Test
    void query_gateway_should_record_deny_when_attribute_contributor_fails() {
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        QueryGateway gateway = queryGateway(
            queryRouter(),
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                allowScope(),
                Collections.emptyList(),
                auditRecorder,
                new DefaultCrudSpecAttributeResolver(Collections.singletonList(spec -> {
                    throw new IllegalStateException("request context missing");
                }))
            )
        );

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .build();

        CrudException ex = Assertions.assertThrows(CrudException.class, () -> gateway.page(spec));
        Assertions.assertEquals(CrudErrorCode.ATTRIBUTE_CONTRIBUTION_FAILED, ex.getErrorCode());
        Assertions.assertEquals("GOVERNANCE_ATTRIBUTES", ex.getReason());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertFalse(auditRecorder.lastEvent.isAllowed());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.ATTRIBUTE_CONTRIBUTION_FAILED, auditRecorder.lastEvent.getReason());
    }

    @Test
    void default_attribute_resolver_should_strip_additional_reserved_keys_before_contributors() {
        Map<String, Object> manualAttributes = new LinkedHashMap<String, Object>();
        manualAttributes.put("businessAccessEntry", "manual-entry");
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .attributes(manualAttributes)
            .build();
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(
            Collections.singletonList(current -> {
                Map<String, Object> attrs = new LinkedHashMap<String, Object>();
                attrs.put("businessAccessEntry", "server-entry");
                return attrs;
            }),
            Collections.singletonList("businessAccessEntry")
        );

        Map<String, Object> resolved = resolver.resolve(spec);

        Assertions.assertEquals("server-entry", resolved.get("businessAccessEntry"));
        Assertions.assertEquals("manual-entry", spec.getAttributes().get("businessAccessEntry"));
    }

    @Test
    void command_gateway_should_fail_closed_when_scope_missing() {
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        CommandGateway gateway = commandGateway(
            commandRouter(),
            null,
            null,
            new DefaultCrudGovernanceService(
                testMetaRegistry(),
                new SpecValidator(),
                new FixedSubjectResolver(subject("tester")),
                allowPermission(),
                new CrudDataScopeResolver() {
                    @Override
                    public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
                        return CrudDataScope.allowAll();
                    }

                    @Override
                    public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
                        return null;
                    }
                },
                Collections.emptyList(),
                auditRecorder
            )
        );

        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("requestId", "req-denied");
        attributes.put("traceId", "trace-denied");
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .scene("order.create")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(CommandOperation.CREATE)
            .attributes(attributes)
            .build();

        DataScopeDeniedException ex = Assertions.assertThrows(DataScopeDeniedException.class, () -> gateway.action(spec));
        Assertions.assertEquals("granted 范围不能为空", ex.getMessage());
        Assertions.assertEquals("GOVERNANCE_SCOPE", ex.getReason());
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertFalse(auditRecorder.lastEvent.isAllowed());
        Assertions.assertFalse(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals("DATA_SCOPE_DENIED", auditRecorder.lastEvent.getReasonCode());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.DATA_SCOPE_DENIED, auditRecorder.lastEvent.getReason());
        Assertions.assertEquals(CrudErrorStage.GOVERNANCE, auditRecorder.lastEvent.getStage());
        Assertions.assertEquals("java.lang.Object|COMMAND/CREATE|order.create", auditRecorder.lastEvent.getRouteKey());
        Assertions.assertEquals("req-denied", auditRecorder.lastEvent.getRequestId());
        Assertions.assertEquals("trace-denied", auditRecorder.lastEvent.getTraceId());
    }

    @Test
    void query_gateway_should_record_audit_when_execution_throws() {
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        RuntimeException expected = new IllegalStateException("query execution failed");
        QueryGateway gateway = queryGateway(
            new QueryRouter() {
                @Override
                public <R> QueryRoute<R> route(QuerySpec<R> spec) {
                    return new QueryRoute<R>() {
                        @Override
                        public QueryHandler<R> handler() {
                            return new QueryHandler<R>() {
                                @Override
                                public boolean supports(QuerySpec<R> current) {
                                    return true;
                                }

                                @Override
                                public PageResult<R> page(QuerySpec<R> current) {
                                    throw expected;
                                }

                                @Override
                                public List<R> list(QuerySpec<R> current) {
                                    return Collections.emptyList();
                                }

                                @Override
                                public R detail(QuerySpec<R> current) {
                                    return null;
                                }
                            };
                        }
                    };
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

        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("requestId", "req-execution");
        attributes.put("traceId", "trace-execution");
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .scene("order.page")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(QueryOperation.PAGE)
            .attributes(attributes)
            .build();

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> gateway.page(spec));
        Assertions.assertSame(expected, actual);
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertTrue(auditRecorder.lastEvent.isAllowed());
        Assertions.assertFalse(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals("QUERY:PAGE", auditRecorder.lastEvent.getAction().getAction());
        Assertions.assertEquals("EXECUTION_FAILED", auditRecorder.lastEvent.getReasonCode());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.EXECUTION_FAILED, auditRecorder.lastEvent.getReason());
        Assertions.assertEquals(CrudErrorStage.EXECUTE, auditRecorder.lastEvent.getStage());
        Assertions.assertEquals("java.lang.Object|QUERY/PAGE|order.page", auditRecorder.lastEvent.getRouteKey());
        Assertions.assertEquals("req-execution", auditRecorder.lastEvent.getRequestId());
        Assertions.assertEquals("trace-execution", auditRecorder.lastEvent.getTraceId());
    }

    @Test
    void command_gateway_should_record_audit_when_execution_throws() {
        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        RuntimeException expected = new IllegalStateException("command execution failed");
        CommandGateway gateway = commandGateway(
            new CommandRouter() {
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
                                public R action(CommandSpec<P> current) {
                                    throw expected;
                                }
                            };
                        }
                    };
                }
            },
            null,
            null,
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

        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .scene("order.create")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(CommandOperation.CREATE)
            .build();

        RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () -> gateway.action(spec));
        Assertions.assertSame(expected, actual);
        Assertions.assertNotNull(auditRecorder.lastEvent);
        Assertions.assertTrue(auditRecorder.lastEvent.isAllowed());
        Assertions.assertFalse(auditRecorder.lastEvent.isSuccess());
        Assertions.assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, auditRecorder.lastEvent.getOutcome());
        Assertions.assertEquals("COMMAND:CREATE", auditRecorder.lastEvent.getAction().getAction());
        Assertions.assertEquals("EXECUTION_FAILED", auditRecorder.lastEvent.getReasonCode());
        Assertions.assertEquals(CrudGovernanceAuditReasonCode.EXECUTION_FAILED, auditRecorder.lastEvent.getReason());
    }

    @Test
    void command_gateway_should_route_with_effective_spec_attributes() {
        AtomicReference<CommandSpec<Object>> routeSpecRef = new AtomicReference<CommandSpec<Object>>();
        CommandGateway gateway = commandGateway(
            new CommandRouter() {
                @Override
                public <P, R> CommandRoute<P, R> route(CommandSpec<P> spec) {
                    routeSpecRef.set((CommandSpec<Object>) spec);
                    return new CommandRoute<P, R>() {
                        @Override
                        public CommandHandler<P, R> handler() {
                            return new CommandHandler<P, R>() {
                                @Override
                                public boolean supports(CommandSpec<P> current) {
                                    return true;
                                }

                                @Override
                                public R action(CommandSpec<P> current) {
                                    return null;
                                }
                            };
                        }
                    };
                }
            },
            null,
            null,
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
                    attrs.put("requestId", "server-request");
                    return attrs;
                }))
            )
        );

        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .scene("order.create")
            .rootType(Object.class)
            .resultType(Object.class)
            .op(CommandOperation.CREATE)
            .build();

        gateway.action(spec);

        Assertions.assertNotNull(routeSpecRef.get());
        Assertions.assertEquals("server-request", routeSpecRef.get().getAttributes().get("requestId"));
        Assertions.assertNull(spec.getAttributes().get("requestId"));
    }

    private QueryRouter queryRouter() {
        return new QueryRouter() {
            @Override
            public <R> QueryRoute<R> route(QuerySpec<R> spec) {
                return new QueryRoute<R>() {
                    @Override
                    public QueryHandler<R> handler() {
                        return new QueryHandler<R>() {
                            @Override
                            public boolean supports(QuerySpec<R> current) {
                                return true;
                            }

                            @Override
                            public PageResult<R> page(QuerySpec<R> current) {
                                return new PageResult<R>(Collections.emptyList(), 0, 1, 10);
                            }

                            @Override
                            public List<R> list(QuerySpec<R> current) {
                                return Collections.emptyList();
                            }

                            @Override
                            public R detail(QuerySpec<R> current) {
                                return null;
                            }
                        };
                    }
                };
            }
        };
    }

    private CommandRouter commandRouter() {
        return new CommandRouter() {
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
                            public R action(CommandSpec<P> current) {
                                return null;
                            }
                        };
                    }
                };
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
