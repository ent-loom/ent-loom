package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.capability.dao.EntityDaoBatchExecutionResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchReadResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteOperation;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteRequest;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationContractTest {
    @Test
    void result_is_bound_to_one_invocation_and_denial_has_no_scope_or_fields() {
        AuthorizationRequest request = request("leave-1");
        AuthorizationFields fields = AuthorizationFields.of(Collections.singletonMap(
            AuthorizationFields.Use.RETURN, Collections.singleton("status")));
        AuthorizationResult granted = AuthorizationResult.granted(request,
            AuthorizationScope.equal("studentId", "student-1"), fields);
        assertEquals(AccessDecision.ALLOW, granted.getDecision());
        assertSame(request, granted.getRequest());
        granted.requireApplicable(request);
        assertThrows(PermissionDeniedException.class, () -> granted.requireApplicable(request("leave-1")));
        assertThrows(PermissionDeniedException.class, () -> granted.requireApplicable(request("leave-2")));
        assertThrows(NullPointerException.class, () -> AuthorizationResult.granted(request, null, fields));
        assertThrows(NullPointerException.class, () -> AuthorizationResult.granted(request, AuthorizationScope.all(), null));

        AuthorizationResult denied = AuthorizationResult.denied(request);
        assertEquals(AccessDecision.DENY, denied.getDecision());
        assertSame(AuthorizationScope.none(), denied.getScope());
        assertFalse(denied.getFields().allows(AuthorizationFields.Use.RETURN, "status"));
        assertThrows(PermissionDeniedException.class, () -> denied.requireApplicable(request));
    }

    @Test
    void dao_context_preserves_request_binding_and_rejects_resource_or_factory_downgrade() {
        AuthorizationRequest request = request("leave-1");
        AuthorizationResult granted = AuthorizationResult.granted(request,
            AuthorizationScope.equal("studentId", "student-1"), AuthorizationFields.none());
        EntityDaoAuthorizationContext context = EntityDaoAuthorizationContext.of(request, granted);
        context.requireApplicable(request);
        context.requireResource("leave");
        assertSame(request, context.getRequest());
        assertEquals("view", context.getOperation().getOperationCode());
        assertEquals("leave-1", context.getTargetId());
        assertEquals(AuthorizationScope.Kind.EQUAL, context.getScope().getKind());

        assertThrows(PermissionDeniedException.class,
            () -> context.requireApplicable(request("leave-1")));
        assertThrows(PermissionDeniedException.class,
            () -> context.requireApplicable(new AuthorizationRequest(
                request.getVisitor(), new BusinessOperation("other", "view"), "leave-1")));
        assertThrows(PermissionDeniedException.class, () -> context.requireResource("other"));
        assertThrows(PermissionDeniedException.class, () -> context.requireResource(null));
        assertEquals("leave-1", context.requireSingleTarget());

        AuthorizationRequest targetlessRequest = request(null);
        AuthorizationResult targetlessResult = AuthorizationResult.granted(targetlessRequest,
            AuthorizationScope.all(), AuthorizationFields.none());
        EntityDaoAuthorizationContext targetlessContext = EntityDaoAuthorizationContext.of(
            targetlessRequest, targetlessResult
        );
        assertThrows(PermissionDeniedException.class, targetlessContext::requireSingleTarget);

        EntityDaoBatchAuthorizationContext batch = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(context)
        );
        assertEquals(Collections.singletonList("leave-1"), batch.getTargetIds());
        assertSame(context.getVisitor(), batch.getVisitor());
        batch.requireResource("leave");
        assertThrows(UnsupportedOperationException.class, () -> batch.getAuthorizations().clear());
        assertThrows(PermissionDeniedException.class, () ->
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(context, context))
        );
        assertThrows(ValidationException.class, () ->
            EntityDaoBatchAuthorizationContext.of(Collections.<EntityDaoAuthorizationContext>emptyList())
        );
        List<EntityDaoAuthorizationContext> oversized = new ArrayList<EntityDaoAuthorizationContext>();
        for (int i = 0; i < EntityDaoBatchAuthorizationContext.DEFAULT_MAX_TARGETS + 1; i++) {
            AuthorizationRequest targetRequest = request("leave-" + i);
            oversized.add(EntityDaoAuthorizationContext.of(targetRequest, AuthorizationResult.granted(
                targetRequest, AuthorizationScope.all(), AuthorizationFields.none()
            )));
        }
        assertThrows(ValidationException.class, () ->
            EntityDaoBatchAuthorizationContext.of(oversized)
        );

        AuthorizationResult denied = AuthorizationResult.denied(request);
        assertThrows(PermissionDeniedException.class,
            () -> EntityDaoAuthorizationContext.of(request, denied));
        assertThrows(NullPointerException.class,
            () -> EntityDaoAuthorizationContext.of(null, granted));

        EntityDaoFactory factory = new EntityDaoFactory() {
            @Override
            public <T, ID> EntityDao<T, ID> scoped(
                EntityType<T, ID> entityType,
                com.entloom.crud.core.capability.dao.EntityAccessScope scope
            ) {
                return null;
            }
        };
        assertThrows(PermissionDeniedException.class,
            () -> factory.scopedAuthorized(EntityType.of(String.class, Long.class), context));
        assertThrows(PermissionDeniedException.class,
            () -> factory.scopedAuthorizedBatch(
                EntityType.of(String.class, Long.class),
                EntityDaoBatchAuthorizationContext.of(Collections.singletonList(context))
            ));
        assertThrows(PermissionDeniedException.class,
            () -> factory.scopedAuthorizedBatchWrite(
                EntityType.of(String.class, Long.class),
                EntityDaoBatchAuthorizationContext.of(Collections.singletonList(context))
            ));
    }

    @Test
    void batch_keeps_all_none_and_restricted_scope_per_target() {
        VisitorContext visitor = request("scope-1").getVisitor();
        EntityDaoAuthorizationContext all = batchAuthorization(visitor, "scope-1", AuthorizationScope.all());
        EntityDaoAuthorizationContext none = batchAuthorization(visitor, "scope-2", AuthorizationScope.none());
        EntityDaoAuthorizationContext restricted = batchAuthorization(
            visitor, "scope-3", AuthorizationScope.equal("tenantId", "tenant-1")
        );

        EntityDaoBatchAuthorizationContext batch = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(all, none, restricted)
        );

        assertEquals(Arrays.asList("scope-1", "scope-2", "scope-3"), batch.getTargetIds());
        assertEquals(Arrays.asList("scope-1"), batch.getAllScopeTargetIds());
        assertEquals(Arrays.asList("scope-2"), batch.getNoDataTargetIds());
        assertEquals(AuthorizationScope.Kind.ALL, batch.getScopesByTarget().get("scope-1").getKind());
        assertEquals(AuthorizationScope.Kind.NONE, batch.getScopesByTarget().get("scope-2").getKind());
        assertEquals(AuthorizationScope.Kind.EQUAL, batch.getScopesByTarget().get("scope-3").getKind());
        assertEquals(AuthorizationScope.Kind.EQUAL,
            batch.intersectScopes(AuthorizationScope.equal("region", "east"))
                .get("scope-1").getKind());
        assertEquals(AuthorizationScope.Kind.NONE,
            batch.intersectScopes(AuthorizationScope.equal("region", "east"))
                .get("scope-2").getKind());
        assertEquals(AuthorizationScope.Kind.AND,
            batch.intersectScopes(AuthorizationScope.equal("region", "east"))
                .get("scope-3").getKind());
        assertThrows(UnsupportedOperationException.class, () -> batch.getScopesByTarget().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.intersectScopes(
            AuthorizationScope.all()
        ).clear());
    }

    @Test
    void batch_write_request_binds_targets_versions_and_all_or_nothing() {
        VisitorContext visitor = request("1").getVisitor();
        EntityDaoAuthorizationContext first = batchAuthorization(
            visitor, "1", AuthorizationScope.equal("tenantId", "tenant-a"), "update"
        );
        EntityDaoAuthorizationContext second = batchAuthorization(
            visitor, "2", AuthorizationScope.equal("tenantId", "tenant-a"), "update"
        );
        EntityDaoBatchAuthorizationContext authorization = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(first, second)
        );

        UpdatePatch<String> firstPatch = writePatch(1L, 3L);
        UpdatePatch<String> secondPatch = writePatch(2L, 4L);
        EntityDaoBatchWriteRequest<String, Long> write = EntityDaoBatchWriteRequest.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchWriteRequest.Item.update("1", 1L, firstPatch),
                EntityDaoBatchWriteRequest.Item.update("2", 2L, secondPatch)
            )
        );

        assertEquals(EntityDaoBatchAtomicity.ALL_OR_NOTHING, write.getAtomicity());
        assertEquals(EntityDaoBatchWriteOperation.UPDATE, write.getItems().get(0).getOperation());
        assertEquals(Long.valueOf(3L), write.getItems().get(0).getExpectedVersion());
        assertEquals(EntityDaoBatchWriteOperation.UPDATE, write.getItems().get(1).getOperation());
        assertEquals(Long.valueOf(4L), write.getItems().get(1).getExpectedVersion());
        assertThrows(UnsupportedOperationException.class, () -> write.getItems().clear());

        EntityDaoBatchWriteRequest<String, Long> delete = EntityDaoBatchWriteRequest.of(
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(
                batchAuthorization(visitor, "1", AuthorizationScope.all(), "delete"),
                batchAuthorization(visitor, "2", AuthorizationScope.all(), "delete"))),
            Arrays.asList(
                EntityDaoBatchWriteRequest.Item.delete("1", 1L),
                EntityDaoBatchWriteRequest.Item.delete("2", 2L, 4L)
            )
        );
        assertEquals(EntityDaoBatchWriteOperation.DELETE, delete.getItems().get(1).getOperation());
        assertEquals(Long.valueOf(4L), delete.getItems().get(1).getExpectedVersion());

        assertThrows(PermissionDeniedException.class, () -> EntityDaoBatchWriteRequest.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchWriteRequest.Item.update("1", 1L, secondPatch),
                EntityDaoBatchWriteRequest.Item.update("2", 2L, firstPatch)
            )
        ));
        assertThrows(ValidationException.class, () -> EntityDaoBatchWriteRequest.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchWriteRequest.Item.update("1", 1L, firstPatch),
                EntityDaoBatchWriteRequest.Item.delete("2", 2L)
            )
        ));
        write.requireApplicable(authorization);
        assertThrows(PermissionDeniedException.class, () -> write.requireApplicable(
            EntityDaoBatchAuthorizationContext.of(Arrays.asList(first, second))));
        assertThrows(PermissionDeniedException.class, () -> EntityDaoBatchWriteRequest.of(
            authorization, Arrays.asList(
                EntityDaoBatchWriteRequest.Item.delete("1", 1L),
                EntityDaoBatchWriteRequest.Item.delete("2", 2L))));
    }

    private static UpdatePatch<String> writePatch(Long id, Long version) {
        return new UpdatePatch<String>() {
            public Class<String> getEntityType() { return String.class; }
            public String getEntity() { return "updated"; }
            public Object getId() { return id; }
            public Long getExpectedVersion() { return version; }
            public Long getLongId() { return id.longValue(); }
            public java.util.Set<String> getPresentFields() {
                return Collections.singleton("name");
            }
            public java.util.Set<String> getPersistableFields() {
                return Collections.singleton("name");
            }
            public Map<String, Object> getValuesForDelegate() {
                return Collections.<String, Object>singletonMap("name", "updated");
            }
            @SuppressWarnings("unchecked")
            public <V> V get(String field) { return (V) getValuesForDelegate().get(field); }
            public <V> V get(String field, Class<V> type) {
                return type.cast(getValuesForDelegate().get(field));
            }
        };
    }

    @Test
    void batch_execution_result_requires_authorized_targets_in_order() {
        VisitorContext visitor = request("result-1").getVisitor();
        EntityDaoAuthorizationContext first = batchAuthorization(
            visitor, "result-1", AuthorizationScope.all()
        );
        EntityDaoAuthorizationContext second = batchAuthorization(
            visitor, "result-2", AuthorizationScope.all()
        );
        EntityDaoBatchAuthorizationContext authorization = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(first, second)
        );

        EntityDaoBatchExecutionResult result = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("result-1"),
                EntityDaoBatchExecutionResult.Item.noData("result-2")
            )
        );
        assertEquals(EntityDaoBatchExecutionResult.Status.SUCCEEDED,
            result.getItems().get(0).getStatus());
        assertEquals(EntityDaoBatchExecutionResult.Status.NO_DATA,
            result.getItemsByTarget().get("result-2").getStatus());
        assertEquals(EntityDaoBatchAtomicity.ALL_OR_NOTHING, result.getAtomicity());
        assertFalse(result.hasFailure());
        assertThrows(UnsupportedOperationException.class, () -> result.getItems().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getItemsByTarget().clear());

        EntityDaoBatchExecutionResult failed = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.versionConflict("result-1"),
                EntityDaoBatchExecutionResult.Item.executionFailed("result-2")
            )
        );
        assertTrue(failed.hasFailure());
        EntityDaoBatchExecutionResult partial = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("result-1"),
                EntityDaoBatchExecutionResult.Item.executionFailed("result-2")
            ),
            EntityDaoBatchAtomicity.EXPLICIT_PARTIAL
        );
        assertEquals(EntityDaoBatchAtomicity.EXPLICIT_PARTIAL, partial.getAtomicity());
        assertThrows(ValidationException.class, () -> EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("result-1"),
                EntityDaoBatchExecutionResult.Item.executionFailed("result-2")
            )
        ));
        assertThrows(ValidationException.class, () -> EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("result-2"),
                EntityDaoBatchExecutionResult.Item.succeeded("result-1")
            )
        ));
    }

    @Test
    void batch_read_result_only_accepts_values_for_succeeded_targets() {
        VisitorContext visitor = request("read-1").getVisitor();
        EntityDaoAuthorizationContext first = batchAuthorization(
            visitor, "read-1", AuthorizationScope.all()
        );
        EntityDaoAuthorizationContext second = batchAuthorization(
            visitor, "read-2", AuthorizationScope.all()
        );
        EntityDaoBatchAuthorizationContext authorization = EntityDaoBatchAuthorizationContext.of(
            Arrays.asList(first, second)
        );
        EntityDaoBatchExecutionResult execution = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("read-1"),
                EntityDaoBatchExecutionResult.Item.noData("read-2")
            )
        );
        Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        values.put("read-1", "实体一");

        EntityDaoBatchReadResult<String, String> result = EntityDaoBatchReadResult.of(execution, values);
        assertEquals("实体一", result.getValues().get("read-1"));
        assertThrows(UnsupportedOperationException.class, () -> result.getValues().clear());

        EntityDaoBatchExecutionResult orderedExecution = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("read-1"),
                EntityDaoBatchExecutionResult.Item.succeeded("read-2")
            )
        );
        Map<String, String> reversedValues = new java.util.LinkedHashMap<String, String>();
        reversedValues.put("read-2", "实体二");
        reversedValues.put("read-1", "实体一");
        EntityDaoBatchReadResult<String, String> orderedResult = EntityDaoBatchReadResult.of(
            orderedExecution, reversedValues
        );
        assertEquals(Arrays.asList("read-1", "read-2"),
            new ArrayList<String>(orderedResult.getValues().keySet()));

        Map<String, String> noDataValue = Collections.singletonMap("read-2", "越权值");
        assertThrows(ValidationException.class,
            () -> EntityDaoBatchReadResult.of(execution, noDataValue));
        assertThrows(ValidationException.class,
            () -> EntityDaoBatchReadResult.of(execution, Collections.singletonMap("unknown", "未知值")));

        EntityDaoBatchExecutionResult missingValueExecution = EntityDaoBatchExecutionResult.of(
            authorization,
            Arrays.asList(
                EntityDaoBatchExecutionResult.Item.succeeded("read-1"),
                EntityDaoBatchExecutionResult.Item.succeeded("read-2")
            )
        );
        assertThrows(ValidationException.class,
            () -> EntityDaoBatchReadResult.of(missingValueExecution, values));
    }

    @Test
    void batch_atomicity_requires_explicit_transaction_capability() {
        CrudWriteTransactionExecutor unsupported = new CrudWriteTransactionExecutor() {
            @Override
            public <T> T execute(CrudWriteTransactionPolicy policy,
                                 com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback<T> callback) {
                return callback.execute();
            }
        };
        assertFalse(EntityDaoBatchAtomicity.ALL_OR_NOTHING.isSupportedBy(unsupported));
        assertThrows(ValidationException.class,
            () -> EntityDaoBatchAtomicity.ALL_OR_NOTHING.requireSupportedBy(unsupported));

        CrudWriteTransactionExecutor supported = new CapabilityTransactionExecutor();
        assertTrue(EntityDaoBatchAtomicity.ALL_OR_NOTHING.isSupportedBy(supported));
        assertFalse(EntityDaoBatchAtomicity.EXPLICIT_PARTIAL.isSupportedBy(supported));
    }

    @Test
    void paired_scope_keeps_and_within_each_branch_and_or_between_branches() {
        AuthorizationScope scope = AuthorizationScope.or(Arrays.asList(
            AuthorizationScope.and(Arrays.asList(
                AuthorizationScope.equal("schoolId", "school-a"), AuthorizationScope.equal("classId", "class-1"))),
            AuthorizationScope.and(Arrays.asList(
                AuthorizationScope.equal("schoolId", "school-b"), AuthorizationScope.equal("classId", "class-2")))));
        assertEquals(AuthorizationScope.Kind.OR, scope.getKind());
        assertEquals(2, scope.getChildren().size());
        assertEquals(AuthorizationScope.Kind.AND, scope.getChildren().get(0).getKind());
        assertEquals("class-1", scope.getChildren().get(0).getChildren().get(1).getValues().get(0));
        assertEquals("class-2", scope.getChildren().get(1).getChildren().get(1).getValues().get(0));
        assertThrows(UnsupportedOperationException.class, () -> scope.getChildren().clear());
        assertThrows(IllegalArgumentException.class, () -> AuthorizationScope.and(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> AuthorizationScope.in("schoolId", Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> AuthorizationScope.equal("schoolId", new Date()));
        assertThrows(IllegalArgumentException.class, () -> AuthorizationScope.equal("schoolId", Double.NaN));
    }

    @Test
    void field_uses_are_independent_and_snapshots_cannot_be_widened() {
        Map<AuthorizationFields.Use, java.util.Collection<String>> source =
            new EnumMap<AuthorizationFields.Use, java.util.Collection<String>>(AuthorizationFields.Use.class);
        source.put(AuthorizationFields.Use.RETURN, Collections.singleton("status"));
        source.put(AuthorizationFields.Use.FILTER, Collections.singleton("studentId"));
        AuthorizationFields fields = AuthorizationFields.of(source);
        source.clear();
        assertTrue(fields.allows(AuthorizationFields.Use.RETURN, "status"));
        assertFalse(fields.allows(AuthorizationFields.Use.WRITE, "status"));
        assertFalse(fields.allows(AuthorizationFields.Use.RETURN, "studentId"));
        assertTrue(fields.allows(AuthorizationFields.Use.FILTER, "studentId"));
        assertThrows(UnsupportedOperationException.class, () -> fields.snapshot().clear());
        assertThrows(UnsupportedOperationException.class, () -> fields.snapshot()
            .get(AuthorizationFields.Use.RETURN).add("secret"));
    }

    private static AuthorizationRequest request(String targetId) {
        return new AuthorizationRequest(VisitorContext.anonymous("base", "request-1", "trace-1"),
            new BusinessOperation("leave", "view"), targetId);
    }

    private static EntityDaoAuthorizationContext batchAuthorization(
        VisitorContext visitor, String targetId, AuthorizationScope scope
    ) {
        return batchAuthorization(visitor, targetId, scope, "view");
    }

    private static EntityDaoAuthorizationContext batchAuthorization(
        VisitorContext visitor, String targetId, AuthorizationScope scope, String operation
    ) {
        AuthorizationRequest request = new AuthorizationRequest(
            visitor, new BusinessOperation("leave", operation), targetId
        );
        return EntityDaoAuthorizationContext.of(
            request, AuthorizationResult.granted(request, scope, AuthorizationFields.none())
        );
    }

    private static final class CapabilityTransactionExecutor
        implements CrudWriteTransactionExecutor, CrudBatchTransactionCapability {
        @Override
        public <T> T execute(
            CrudWriteTransactionPolicy policy,
            com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback<T> callback
        ) {
            return callback.execute();
        }

        @Override
        public boolean supportsBatchAtomicity(EntityDaoBatchAtomicity atomicity) {
            return atomicity == EntityDaoBatchAtomicity.ALL_OR_NOTHING;
        }
    }
}
