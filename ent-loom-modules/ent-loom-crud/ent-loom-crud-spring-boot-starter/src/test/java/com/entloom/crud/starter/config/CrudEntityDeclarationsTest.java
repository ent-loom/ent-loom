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
              allowed-operations: [detail]
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

    @Test
    void 大写枚举与大小写和分隔符别名编译到相同协议() {
        for (String operation : List.of("FIND_ONE", "find_one", "find-one", "Find-One")) {
            var files = declarations("crud: {operations: {view: [" + operation + "]}, allowed-operations: [" + operation + "]}");
            assertThat(files.options().get("order").getOperations().get("view")).containsExactly("FIND_ONE");
            assertThat(files.allowedOperations().get("order")).containsExactly(CrudOperationKey.of(QueryOperation.FIND_ONE));
        }
        for (String use : List.of("WRITE", "write", "Write")) {
            for (String mode : List.of("INCLUDE", "include", "Include")) {
                var files = declarations("crud: {access: {customer: {fields: {" + use + ": {mode: " + mode + ", fields: [remark]}}}}}");
                var plan = files.options().get("order").getAccess().getAccess().get("customer").getFields().get(
                    com.entloom.crud.core.governance.authorization.AuthorizationFields.Use.WRITE);
                assertThat(plan.getMode()).isEqualTo(CrudEntityDeclarations.FieldMode.INCLUDE);
                assertThat(plan.getFields()).containsExactly("remark");
            }
        }
    }

    @Test
    void 归一化后重复枚举不能覆盖或静默去重() {
        for (String content : List.of(
                "crud: {access: {customer: {fields: {WRITE: {mode: INCLUDE, fields: [remark]}, write: {mode: ALL}}}}}",
                "crud: {allowed-operations: [FIND_ONE, find-one]}",
                "crud: {operations: {update: [UPDATE_BATCH, update-batch]}}")) {
            assertThatThrownBy(() -> declarations(content)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复").hasMessageContaining("order.yml").hasMessageContaining("路径=crud");
        }
    }

    @Test
    void 宽松枚举仍拒绝未知值空值及非法字段模式组合() {
        for (String content : List.of("crud: {policy: ''}",
                "crud: {allowed-operations: [findOne]}", "crud: {allowed-operations: [UNKNOWN]}",
                "crud: {access: {customer: {fields: {WRIT: {mode: ALL}}}}}",
                "crud: {access: {customer: {fields: {WRITE: {mode: unknown}}}}}",
                "crud: {access: {customer: {fields: {WRITE: {mode: ''}}}}}",
                "crud: {access: {customer: {fields: {Write: {mode: Include, fields: []}}}}}",
                "crud: {access: {customer: {fields: {Write: {mode: All, fields: [remark]}}}}}")) {
            assertThatThrownBy(() -> declarations(content)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order.yml").hasMessageContaining("路径=crud");
        }
    }

    private static class Order { }
}
