package com.entloom.meta.starter;

import com.entloom.ddl.starter.EntDdlAutoConfiguration;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.ddl.annotations.EntDdlIndex;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class EntityDeclarationIntegrationTest {
    @Test
    void 不执行DDL时文档复用实体注解与索引() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
                EntLoomMetaDdlAutoConfiguration.class, EntDdlAutoConfiguration.class))
            .withPropertyValues("ent.loom.meta.entity-class-names=" + Order.class.getName())
            .run(context -> {
                assertThat(context).hasNotFailed();
                Map<String, Object> document = context.getBean(MetaDocAdapter.class).buildOne(Order.class);
                assertThat(document.get("group")).isEqualTo("交易");
                assertThat(document.get("indexes").toString()).contains("idx_order_account", "account_code");
                assertThat(document.get("fields").toString()).contains("ACC-1");
            });
    }

    @EntEntity
    @EntDocEntity(group = "交易")
    @EntDdlIndex(name = "idx_order_account", fields = "accountCode")
    static class Order {
        Long id;
        @EntDocField(example = "ACC-1")
        String accountCode;
    }
}
