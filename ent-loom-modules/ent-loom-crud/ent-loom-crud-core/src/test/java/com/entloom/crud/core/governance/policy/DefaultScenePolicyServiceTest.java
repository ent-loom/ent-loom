package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.adapter.AccessEntryResolver;
import com.entloom.crud.core.adapter.AttributeAccessEntryResolver;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultScenePolicyServiceTest {
    private static final String RESOURCE = "user";
    private static final String SCENE = "activate";

    @Test
    void action_should_fail_closed_when_policy_is_missing() {
        DefaultScenePolicyService service = service(Collections.<ScenePolicy>emptyList());

        ScenePolicyMatch match = service.match(action(), command("web"));
        Assertions.assertTrue(match.isRequired());
        Assertions.assertFalse(match.isMatched());
        Assertions.assertTrue(match.getRejectionReason().contains("未配置"));
    }

    @Test
    void action_should_return_capability_when_key_and_portal_match() {
        ScenePolicy policy = new ScenePolicy(
            new ScenePolicyKey("sdk", RESOURCE, CrudOperationKey.of(CommandOperation.ACTION), SCENE),
            "user:activate",
            new HashSet<String>(Collections.singletonList("web"))
        );

        ScenePolicyMatch match = service(Collections.singletonList(policy)).match(action(), command("web"));

        Assertions.assertTrue(match.isMatched());
        Assertions.assertEquals("user:activate", match.getCapability());
        Assertions.assertEquals("sdk", match.getAccessEntry());
    }

    @Test
    void ordinary_query_should_not_require_policy() {
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .op(QueryOperation.LIST)
            .build();
        CrudResourceAction action = new CrudResourceAction(descriptor(), CrudOperationKey.of(QueryOperation.LIST), "", null);

        ScenePolicyMatch match = service(Collections.<ScenePolicy>emptyList()).match(action, spec);

        Assertions.assertFalse(match.isRequired());
    }

    @Test
    void registry_should_reject_duplicate_key_and_expose_frozen_snapshot() {
        ScenePolicy policy = policy();
        Assertions.assertThrows(ValidationException.class, () -> new ScenePolicyRegistry(Arrays.asList(policy, policy)));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new ScenePolicyRegistry(Collections.singletonList(policy)).snapshot().clear());
    }

    private DefaultScenePolicyService service(java.util.Collection<ScenePolicy> policies) {
        return new DefaultScenePolicyService(new ScenePolicyRegistry(policies), new AttributeAccessEntryResolver());
    }

    private ScenePolicy policy() {
        return new ScenePolicy(
            new ScenePolicyKey("sdk", RESOURCE, CrudOperationKey.of(CommandOperation.ACTION), SCENE),
            "user:activate",
            Collections.<String>emptySet()
        );
    }

    private CommandSpec<Object> command(String portal) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessEntryResolver.ATTRIBUTE_KEY, "sdk");
        attributes.put(DefaultScenePolicyService.PORTAL_ATTRIBUTE_KEY, portal);
        return CommandSpec.builder()
            .rootType(TestEntity.class)
            .op(CommandOperation.ACTION)
            .scene(SCENE)
            .attributes(attributes)
            .build();
    }

    private CrudResourceAction action() {
        return new CrudResourceAction(descriptor(), CrudOperationKey.of(CommandOperation.ACTION), SCENE, null);
    }

    private ResourceDescriptor descriptor() {
        return new ResourceDescriptor(TestEntity.class, RESOURCE, null, Collections.<String>emptyList());
    }

    private static final class TestEntity {
    }
}
