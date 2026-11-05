package com.entloom.meta.starter;

import com.entloom.ddl.starter.EntDdlAutoConfiguration;
import com.entloom.declaration.EntityDeclarations;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.annotations.EntEntity;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;

/** 单一实体文件经过真实组件装配进入 DDL 与 DOC 模型。 */
class EntityDeclarationIntegrationTest {
    private ApplicationContextRunner runner(String yaml) {
        return runner(yaml, "order.yml");
    }

    private ApplicationContextRunner runner(String yaml, String filename) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
                EntLoomMetaDdlAutoConfiguration.class, EntDdlAutoConfiguration.class))
            .withPropertyValues("ent.loom.meta.entity-class-names=" + Order.class.getName())
            .withBean(EntityDeclarations.class, () -> new EntityDeclarations(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8), filename) {
                @Override public String getFilename() { return filename; }
            }));
    }

    @Test
    void 不执行DDL时仍校验文件并复用索引展示() {
        runner("""
            ddl:
              indexes:
                - name: idx_order_account
                  fields: [accountCode]
            doc:
              group: 交易
              fields:
                accountCode: {example: ACC-1}
            """).run(context -> {
                assertThat(context).hasNotFailed();
                var adapter = context.getBean(MetaDocAdapter.class);
                Map<String, Object> document = adapter.buildOne(Order.class);
                assertThat(document.get("group")).isEqualTo("交易");
                assertThat(document.get("indexes").toString()).contains("idx_order_account", "account_code");
                assertThat(document.get("fields").toString()).contains("ACC-1");
            });
    }

    @Test
    void 未扫描实体与禁用组件拒绝启动() {
        runner("ddl: {indexes: [{fields: [missing]}]}").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("实体=order").hasStackTraceContaining("路径=ddl.indexes");
        });
        runner("doc: {group: 交易}").withPropertyValues("ent.loom.meta.doc.enabled=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("组件未安装、未启用");
        });
    }

    @Test
    void 启动期仍拒绝声明实体漏扫描() {
        runner("ddl: {}", "unscanned.yml").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("实体未注册或未扫描: unscanned");
        });
    }

    @EntEntity
    static class Order { Long id; String accountCode; }
}
