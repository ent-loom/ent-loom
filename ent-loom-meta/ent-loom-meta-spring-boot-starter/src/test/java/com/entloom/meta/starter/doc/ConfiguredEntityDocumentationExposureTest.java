package com.entloom.meta.starter.doc;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.doc.core.spi.DocOverrideProvider;
import com.entloom.doc.core.spi.DocEntityOverride;
import com.entloom.doc.core.spi.DocFieldOverride;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;

class ConfiguredEntityDocumentationExposureTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class, EntityDocumentationContractAutoConfiguration.class))
        .withBean(CrudSubjectResolver.class, () -> () -> subject("reader"))
        .withBean(DocOverrideProvider.class, () -> (entityClass, resourceCode) ->
            entityClass == Customer.class
                ? DocEntityOverride.builder().field(DocFieldOverride.builder("secret").hidden(true).build()).build()
                : DocEntityOverride.builder().build())
        .withPropertyValues("ent.loom.meta.entity-class-names[0]=" + Customer.class.getName(),
            "ent.loom.meta.entity-class-names[1]=" + Product.class.getName(),
            "ent.loom.doc.contract.enabled=true",
            "ent.loom.doc.contract.exposure.subject-ids[0]=reader");

    @Test
    @SuppressWarnings("unchecked")
    void 配置直接生成文档且不公开未列出字段或隐藏字段() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.include-entities[0]=customer",
            "ent.loom.doc.contract.exposure.fields.customer[0]=id",
            "ent.loom.doc.contract.exposure.fields.customer[1]=secret").run(context -> {
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
    void 未授权主体不公开实体() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.subject-ids[0]=other")
            .run(context -> assertThat((List<?>) context.getBean(EntityDocumentationContractService.class).build().get("entities")).isEmpty());
        EntityDocumentationExposurePolicyResolver resolver = new ConfiguredEntityDocumentationExposurePolicyResolver(
            new EntityDocumentationContractProperties.Exposure(), List.of());
        assertThat(resolver.resolve(null).isEntityExposed(null)).isFalse();
        assertThat(resolver.resolve(subject("reader")).isEntityExposed(null)).isFalse();
    }

    @Test
    void 自定义策略完整替换配置策略() {
        EntityDocumentationExposurePolicyResolver custom = subject -> EntityDocumentationExposurePolicy.denyAll();
        runner.withPropertyValues("ent.loom.doc.contract.exposure.fields.missing=id")
            .withBean(EntityDocumentationExposurePolicyResolver.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EntityDocumentationExposurePolicyResolver.class);
            assertThat(context.getBean(EntityDocumentationExposurePolicyResolver.class)).isSameAs(custom);
            assertThat((List<?>) context.getBean(EntityDocumentationContractService.class).build().get("entities")).isEmpty();
        });
    }

    @Test
    void 关闭文档契约后不创建策略和服务() {
        runner.withPropertyValues("ent.loom.doc.contract.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(EntityDocumentationExposurePolicyResolver.class);
            assertThat(context).doesNotHaveBean(EntityDocumentationContractService.class);
        });
    }

    @Test
    void 缺省主体时服务不公开实体() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
                EntityDocumentationContractAutoConfiguration.class))
            .withBean(CrudSubjectResolver.class, () -> () -> subject("reader"))
            .withPropertyValues("ent.loom.meta.entity-class-names[0]=" + Customer.class.getName(),
                "ent.loom.doc.contract.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(EntityDocumentationContractService.class);
                assertThat((List<?>) context.getBean(EntityDocumentationContractService.class)
                    .build().get("entities")).isEmpty();
            });
    }

    @Test
    void 默认展示全部注册实体及非隐藏字段() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            List<Map<String, Object>> entities = entities(context.getBean(EntityDocumentationContractService.class));
            assertThat(entities).extracting(entity -> entity.get("resourceCode")).containsExactly("customer", "product");
            assertThat(fields(entities.get(0))).containsExactly("email", "id");
            assertThat(fields(entities.get(1))).containsExactly("id", "name");
        });
    }

    @Test
    void 排除优先于包含且字段限制不改变实体范围() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.include-entities=customer,product",
            "ent.loom.doc.contract.exposure.exclude-entities=product",
            "ent.loom.doc.contract.exposure.fields.customer=id").run(context -> {
            List<Map<String, Object>> entities = entities(context.getBean(EntityDocumentationContractService.class));
            assertThat(entities).extracting(entity -> entity.get("resourceCode")).containsExactly("customer");
            assertThat(fields(entities.get(0))).containsExactly("id");
        });
        runner.withPropertyValues("ent.loom.doc.contract.exposure.fields.customer=id").run(context -> {
            List<Map<String, Object>> entities = entities(context.getBean(EntityDocumentationContractService.class));
            assertThat(entities).hasSize(2);
            assertThat(fields(entities.get(0))).containsExactly("id");
            assertThat(fields(entities.get(1))).containsExactly("id", "name");
        });
    }

    @Test
    void 默认全量时也支持单独排除() {
        runner.withPropertyValues("ent.loom.doc.contract.exposure.exclude-entities=customer").run(context ->
            assertThat(entities(context.getBean(EntityDocumentationContractService.class)))
                .extracting(entity -> entity.get("resourceCode")).containsExactly("product"));
    }

    @Test
    void 显式空字段集合只公开实体信息() throws IOException {
        String yaml = """
            ent:
              loom:
                doc:
                  contract:
                    exposure:
                      fields:
                        customer: []
            """;
        var sources = new YamlPropertySourceLoader().load("empty-fields",
            new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        runner.withInitializer(context -> sources.forEach(source ->
            context.getEnvironment().getPropertySources().addFirst(source))).run(context -> {
            assertThat(context).hasNotFailed();
            List<Map<String, Object>> entities = entities(context.getBean(EntityDocumentationContractService.class));
            assertThat(entities).hasSize(2);
            assertThat(fields(entities.get(0))).isEmpty();
            assertThat(fields(entities.get(1))).containsExactly("id", "name");
        });
    }

    @Test
    void 未注册实体或未知字段启动失败() {
        for (String property : List.of("include-entities=missing", "exclude-entities=missing",
            "fields.missing=id", "fields.customer=missing", "include-entities=Customer")) {
            runner.withPropertyValues("ent.loom.doc.contract.exposure." + property).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("ent.loom.doc.contract.exposure.");
            });
        }
    }

    @Test
    void 策略保存独立配置快照() {
        runner.run(context -> {
            EntityDocumentationContractProperties.Exposure exposure = new EntityDocumentationContractProperties.Exposure();
            exposure.setSubjectIds(new LinkedHashSet<>(Set.of("reader")));
            exposure.setIncludeEntities(new LinkedHashSet<>(Set.of("customer")));
            exposure.setExcludeEntities(new LinkedHashSet<>());
            exposure.getFields().put("customer", new LinkedHashSet<>(Set.of("id")));
            MetaDocAdapter adapter = context.getBean(MetaDocAdapter.class);
            EntityDocumentationExposurePolicyResolver resolver =
                new ConfiguredEntityDocumentationExposurePolicyResolver(exposure, adapter.models());
            exposure.getSubjectIds().clear();
            exposure.getIncludeEntities().clear();
            exposure.getExcludeEntities().add("customer");
            exposure.getFields().get("customer").add("email");
            List<Map<String, Object>> entities = entities(new EntityDocumentationContractService(
                adapter, () -> subject("reader"), resolver));
            assertThat(entities).extracting(entity -> entity.get("resourceCode")).containsExactly("customer");
            assertThat(fields(entities.get(0))).containsExactly("id");
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entities(EntityDocumentationContractService service) {
        return (List<Map<String, Object>>) service.build().get("entities");
    }

    @SuppressWarnings("unchecked")
    private static List<String> fields(Map<String, Object> entity) {
        return ((List<Map<String, Object>>) entity.get("fields")).stream()
            .map(field -> (String) field.get("property")).toList();
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

    @EntEntity(value = "商品", entity = "product")
    static class Product {
        @EntField("编号") Long id;
        @EntField("名称") String name;
    }
}
