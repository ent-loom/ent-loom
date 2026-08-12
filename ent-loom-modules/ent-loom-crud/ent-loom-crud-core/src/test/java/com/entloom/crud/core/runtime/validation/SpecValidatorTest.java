package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.IdempotencyKeyRequiredException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpecValidatorTest {
    @Test
    void should_normalize_page_request_defaults_without_mutating_original_spec() {
        QuerySpec<Object> spec = baseQuery(QueryOperation.PAGE)
            .page(new PageRequest(0, 0))
            .build();

        QuerySpec<Object> validated = new SpecValidator().validateQuerySpec(spec);

        Assertions.assertEquals(1, validated.getPage().getPage());
        Assertions.assertEquals(10, validated.getPage().getLimit());
        Assertions.assertEquals(0, spec.getPage().getPage());
        Assertions.assertEquals(0, spec.getPage().getLimit());
    }

    @Test
    void should_apply_list_default_limit_and_reject_oversized_limit() {
        QuerySpec<Object> defaultLimit = new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.LIST).build());

        Assertions.assertEquals(Integer.valueOf(200), defaultLimit.getLimit());
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.LIST).limit(1001).build())
        );
    }

    @Test
    void should_reject_find_one_pagination_and_set_unique_limit() {
        QuerySpec<Object> validated = new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.FIND_ONE).build());

        Assertions.assertEquals(Integer.valueOf(2), validated.getLimit());
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.FIND_ONE).page(new PageRequest(1, 10)).build())
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.FIND_ONE).limit(10).build())
        );
    }

    @Test
    void should_set_detail_unique_limit_and_reject_pagination() {
        QuerySpec<Object> validated = new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.DETAIL).build());

        Assertions.assertEquals(Integer.valueOf(2), validated.getLimit());
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateQuerySpec(baseQuery(QueryOperation.DETAIL).page(new PageRequest(1, 10)).build())
        );
    }

    @Test
    void should_require_query_result_type_and_command_result_type() {
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateQuerySpec(QuerySpec.<Object>builder()
                .rootType(Object.class)
                .subject(subject())
                .op(QueryOperation.PAGE)
                .build())
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> new SpecValidator().validateCommandSpec(CommandSpec.<Object>builder()
                .rootType(Object.class)
                .subject(subject())
                .op(CommandOperation.CREATE)
                .build())
        );
    }

    @Test
    void should_require_idempotency_key_when_policy_requires_operation() {
        SpecValidator validator = new SpecValidator(new IdempotencyPolicy(
            IdempotencyPolicy.Mode.OPTIONAL,
            EnumSet.of(CommandOperation.CREATE),
            Collections.<String>emptySet()
        ));
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(Object.class)
            .subject(subject())
            .op(CommandOperation.CREATE)
            .resultType(Object.class)
            .build();

        Assertions.assertThrows(IdempotencyKeyRequiredException.class, () -> validator.validateCommandSpec(spec));
        CommandSpec<Object> validated = validator.validateCommandSpec(spec.toBuilder().idempotencyKey("idem-1").build());
        Assertions.assertEquals("idem-1", validated.getIdempotencyKey());
    }

    private QuerySpec.Builder<Object> baseQuery(QueryOperation op) {
        return QuerySpec.<Object>builder()
            .rootType(Object.class)
            .subject(subject())
            .op(op)
            .resultType(Object.class);
    }

    private SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        return subject;
    }
}
