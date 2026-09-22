package com.entloom.meta.starter.doc;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ConfiguredEntityDocumentationExposureTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class, EntityDocumentationContractAutoConfiguration.class))
        .withBean(CrudSubjectResolver.class, () -> () -> subject("reader"))
        .withBean(DocOverrideProvider.class, () -> (entityClass, resourceCode) ->
            DocEntityOverride.builder().field(DocFieldOverride.builder("secret").hidden(true).build()).build())
        .withPropertyValues("ent.loom.meta.entity-class-names[0]=" + Customer.class.getName(),
            "ent.loom.doc.contract.enabled=true", "ent.loom.doc.contract.exposure.enabled=true",
            "ent.loom.doc.contract.exposure.subject-ids[0]=reader",
            "ent.loom.doc.contract.exposure.fields.customer[0]=id",
            "ent.loom.doc.contract.exposure.fields.customer[1]=secret");

    @Test
    @SuppressWarnings("unchecked")
    void 配置直接生成文档且不公开未列出字段或隐藏字段() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EntityDocumentationExposurePolicyResolver.class);
            Map<String, Object> contract = context.getBean(EntityDocumentationContractService.class).build();
            List<Map<String, Object>> entities = (List<Map<String, Object>>) contract.get("entities");
            assertThat(entities).hasSize(1);
            List<Map<String, Object>> fields = (List<Map<String, Object>>) entities.get(0).get("fields");
            assertThat(fields).extracting(field -> field.get("property")).containsExactly("id");
        });
    }

    @Test
    void 未授权主体或缺省白名单不公开实体() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.subject-ids[0]=other")
            .run(context -> assertThat((List<?>) context.getBean(EntityDocumentationContractService.class).build().get("entities")).isEmpty());
        EntityDocumentationExposurePolicyResolver resolver = new ConfiguredEntityDocumentationExposurePolicyResolver(
            new EntityDocumentationContractProperties.Exposure());
        assertThat(resolver.resolve(null).isEntityExposed(null)).isFalse();
        assertThat(resolver.resolve(subject("reader")).isEntityExposed(null)).isFalse();
    }

    @Test
    void 自定义策略完整替换配置策略() {
        EntityDocumentationExposurePolicyResolver custom = subject -> EntityDocumentationExposurePolicy.denyAll();
        runner.withBean(EntityDocumentationExposurePolicyResolver.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(EntityDocumentationExposurePolicyResolver.class);
            assertThat(context.getBean(EntityDocumentationExposurePolicyResolver.class)).isSameAs(custom);
            assertThat((List<?>) context.getBean(EntityDocumentationContractService.class).build().get("entities")).isEmpty();
        });
    }

    @Test
    void 单独关闭配置策略后不创建服务() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(EntityDocumentationExposurePolicyResolver.class);
            assertThat(context).doesNotHaveBean(EntityDocumentationContractService.class);
        });
    }

    private static SubjectContext subject(String id) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(id);
        return subject;
    }

    @EntEntity(value = "客户", entity = "customer")
    static class Customer {
        @EntField("编号") Long id;
        @EntField("邮箱") String email;
        @EntField("内部信息") String secret;
    }
}
