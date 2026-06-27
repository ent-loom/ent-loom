package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IdempotencyPolicyTest {
    @Test
    void required_mode_should_require_key_for_non_dry_run_commands() {
        IdempotencyPolicy policy = new IdempotencyPolicy(
            IdempotencyPolicy.Mode.REQUIRED,
            Collections.<CommandOperation>emptySet(),
            Collections.<String>emptySet()
        );

        Assertions.assertTrue(policy.isRequired(command(CommandOperation.CREATE, "order.create", false, null)));
        Assertions.assertFalse(policy.isRequired(command(CommandOperation.CREATE, "order.create", true, null)));
    }

    @Test
    void optional_mode_should_require_key_for_configured_ops_or_scenes() {
        LinkedHashSet<String> scenes = new LinkedHashSet<String>();
        scenes.add(" Order.Place ");
        IdempotencyPolicy policy = new IdempotencyPolicy(
            IdempotencyPolicy.Mode.OPTIONAL,
            EnumSet.of(CommandOperation.CREATE),
            scenes
        );

        Assertions.assertTrue(policy.isRequired(command(CommandOperation.CREATE, "any.scene", false, null)));
        Assertions.assertTrue(policy.isRequired(command(CommandOperation.ACTION, "order.place", false, null)));
        Assertions.assertFalse(policy.isRequired(command(CommandOperation.UPDATE, "order.update", false, null)));
    }

    @Test
    void should_apply_only_when_non_dry_run_command_has_text_key() {
        IdempotencyPolicy policy = new IdempotencyPolicy();

        Assertions.assertFalse(policy.shouldApply(command(CommandOperation.CREATE, "order.create", false, null)));
        Assertions.assertFalse(policy.shouldApply(command(CommandOperation.CREATE, "order.create", false, "  ")));
        Assertions.assertFalse(policy.shouldApply(command(CommandOperation.CREATE, "order.create", true, "idem-1")));
        Assertions.assertTrue(policy.shouldApply(command(CommandOperation.CREATE, "order.create", false, "idem-1")));
    }

    private CommandSpec<Object> command(CommandOperation op, String scene, boolean dryRun, String idempotencyKey) {
        return CommandSpec.<Object>builder()
            .rootType(Object.class)
            .op(op)
            .scene(scene)
            .dryRun(dryRun)
            .idempotencyKey(idempotencyKey)
            .build();
    }
}
