package com.entloom.crud.starter.config;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.declaration.EntityDeclarations;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;

/** 文件语法、动作边界与标准操作白名单的编译合同。 */
class CrudEntityDeclarationsTest {
    private CrudEntityDeclarations declarations(String content) {
        return new CrudEntityDeclarations(new EntityDeclarations(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "order.yml") {
            @Override public String getFilename() { return "order.yml"; }
        }));
    }

    @Test
    void 动作按入口独立编译并规范化边界() {
        var files = declarations("""
            crud:
              allowed-operations: [DETAIL]
              actions:
                PLACE:
                  name: 下单
                  entries:
                    consumer: {capability: place-order}
                    management: {capability: manage-order, portals: [http]}
            """);
        var scenes = ScenePolicyRegistry.create(CrudPolicyAutoConfigurationTest.metas(Order.class), files.scenes(), files.actions(), files.allowedOperations());
        assertThat(scenes.snapshot()).hasSize(2);
        assertThat(scenes.allowsAction("order", "PLACE")).isTrue();
        assertThat(scenes.allowsAction("order", "cancel")).isFalse();
        assertThat(scenes.allowsStandard("order", CrudOperationKey.of(QueryOperation.DETAIL))).isTrue();
        assertThat(scenes.allowsStandard("order", CrudOperationKey.of(QueryOperation.PAGE))).isFalse();
        assertThat(files.scenes().stream().map(policy -> policy.getCapability())).containsExactly("place-order", "manage-order");
    }

    @Test
    void 空动作与标准白名单关闭能力而省略不增加限制() {
        var files = declarations("crud: {actions: {}, allowed-operations: []}");
        var closed = new ScenePolicyRegistry(List.of(), files.actions(), files.allowedOperations());
        assertThat(closed.allowsAction("order", "place")).isFalse();
        assertThat(closed.allowsStandard("order", CrudOperationKey.of(QueryOperation.DETAIL))).isFalse();
        var defaults = declarations("crud: {}");
        assertThat(defaults.actions()).isEmpty();
        assertThat(defaults.allowedOperations()).isEmpty();
    }

    @Test
    void 非法动作输入拒绝且定位到文件实体及路径() {
        for (String content : List.of(
                "crud: {actions: null}",
                "crud: {actions: {place: {name: 下单, entries: {}}}}",
                "crud: {actions: {place: {name: 下单, entries: {consumer: {capability: ' '}}}}}",
                "crud: {actions: {place: {name: 下单, entries: {consumer: {capability: place, portals: [http, http]}}}}}",
                "crud: {actions: {place: {name: 下单, entries: {consumer: {capability: place}}}, PLACE: {name: 下单, entries: {consumer: {capability: place}}}}}",
                "crud: {allowed-operations: [ACTION]}",
                "crud: {allowed-operations: [UNKNOWN]}",
                "crud: {access: JAVA, policy: order}",
                "crud: {http-exposed: null}")) {
            assertThatThrownBy(() -> declarations(content)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order.yml").hasMessageContaining("实体=order").hasMessageContaining("路径=crud");
        }
    }

    private static class Order { }
}
