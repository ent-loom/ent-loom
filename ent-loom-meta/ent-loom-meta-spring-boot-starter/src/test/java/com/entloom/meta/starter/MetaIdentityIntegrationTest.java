package com.entloom.meta.starter;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudIdPolicyDefaults;
import com.entloom.crud.starter.config.CrudProperties;
import com.entloom.crud.starter.config.module.CrudCommonConfiguration;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.ddl.core.MysqlCreateTableSqlBuilder;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.starter.EntDdlAutoConfiguration;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.enums.EntIdPolicy;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import com.entloom.meta.starter.scanfixture.nested.ScannedEntities;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

class MetaIdentityIntegrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
            EntLoomMetaDdlAutoConfiguration.class, EntDdlAutoConfiguration.class))
        .withUserConfiguration(CrudRegistryConfiguration.class)
        .withPropertyValues("ent.loom.meta.defaults.id-policy=DATABASE", "ent.loom.meta.doc.enabled=false");

    @Test
    void Ddl缺省复用Boot包扫描得到的Meta实体() {
        runner.withUserConfiguration(EntLoomMetaAutoConfigurationTest.BootPackagesConfiguration.class)
            .run(context -> {
                assertNull(context.getStartupFailure());
                java.util.List<DdlEntityMetadata> models = context.getBean(MetadataLoader.class)
                    .load(new MetadataLoadRequest(Collections.emptyList(), Collections.emptyList()));
                DdlEntityMetadata entity = models.stream()
                    .filter(model -> model.entityClassName().equals(ScannedEntities.MetaEntity.class.getName()))
                    .findFirst().orElseThrow();
                assertEquals(context.getBean(EntityMetaRegistry.class)
                    .getEntityMeta(ScannedEntities.MetaEntity.class).getTable(), entity.tableName());
                assertEquals(GenerationStrategy.AUTO_INCREMENT, entity.fields().get(0).generationStrategy());
                assertEquals(EntityIdPolicy.GENERATED, context.getBean(EntityMetaRegistry.class)
                    .getEntityMeta(ScannedEntities.MetaEntity.class).getIdPolicy());
            });
    }

    @Test
    void 统一默认配置驱动Crud和Ddl且允许实体例外() {
        runner.withPropertyValues(entities(DefaultEntity.class, AssignedEntity.class, NativeAssignedEntity.class,
            ApplicationEntity.class))
            .run(context -> {
                assertNull(context.getStartupFailure());
                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                assertEquals(EntityIdPolicy.GENERATED, registry.getEntityMeta(DefaultEntity.class).getIdPolicy());
                assertEquals(EntityIdPolicy.EXPLICIT, registry.getEntityMeta(AssignedEntity.class).getIdPolicy());
                assertEquals(EntityIdPolicy.EXPLICIT, registry.getEntityMeta(NativeAssignedEntity.class).getIdPolicy());
                assertEquals(EntityIdPolicy.APPLICATION, registry.getEntityMeta(ApplicationEntity.class).getIdPolicy());
                MetaCrudAdapter adapter = context.getBean(MetaCrudAdapter.class);
                assertEquals(MetaValueSource.BUSINESS_DEFAULT_CONFIG, adapter.idPolicySource(DefaultEntity.class).source());
                assertEquals("ent.loom.meta.defaults.id-policy", adapter.idPolicySource(DefaultEntity.class).ruleId());
                MetadataLoader loader = context.getBean(MetadataLoader.class);
                assertInstanceOf(MetaDdlMetadataLoader.class, loader);
                for (DdlEntityMetadata model : loader.load(emptyRequest())) {
                    String sql = new MysqlCreateTableSqlBuilder().build(model, "");
                    assertEquals(model.entityClassName().equals(DefaultEntity.class.getName()), sql.contains("AUTO_INCREMENT"));
                }
            });
    }

    @Test
    void 模块默认优先于Meta默认但不覆盖Meta显式声明() {
        runner.withPropertyValues(entities(DefaultEntity.class, DatabaseEntity.class))
            .withPropertyValues("ent.loom.crud.defaults.id-policy=EXPLICIT",
                "ent.loom.ddl.defaults.generation-strategy=NONE")
            .run(context -> {
                assertNull(context.getStartupFailure());
                EntityMetaRegistry registry = context.getBean(EntityMetaRegistry.class);
                assertEquals(EntityIdPolicy.EXPLICIT, registry.getEntityMeta(DefaultEntity.class).getIdPolicy());
                assertEquals(EntityIdPolicy.GENERATED, registry.getEntityMeta(DatabaseEntity.class).getIdPolicy());
                assertEquals(2, context.getBean(MetadataLoader.class).load(emptyRequest()).size());
            });
    }

    @Test
    void Crud显式覆盖与Ddl最终策略不一致应在执行Sql前失败() {
        runner.withPropertyValues(entities(ConflictingEntity.class))
            .run(context -> {
                assertNull(context.getStartupFailure());
                IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> context.getBean(MetadataLoader.class).load(emptyRequest()));
                assertTrue(error.getMessage().contains("主键契约冲突"));
            });
    }

    @Test
    void 数据库生成不支持字符串主键() {
        runner.withPropertyValues(entities(StringIdEntity.class))
            .run(context -> assertThrows(com.entloom.meta.contract.diagnostic.MetaDiagnosticException.class,
                () -> context.getBean(MetadataLoader.class).load(emptyRequest())));
    }

    @Test
    void 宽松诊断且关闭Crud仍必须阻止无效Ddl模型() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
            EntLoomMetaDdlAutoConfiguration.class, EntDdlAutoConfiguration.class))
            .withPropertyValues(entities(StringIdEntity.class))
            .withPropertyValues("ent.loom.meta.defaults.id-policy=DATABASE",
                "ent.loom.meta.diagnostics.fail-fast=false", "ent.loom.meta.crud.enabled=false",
                "ent.loom.meta.doc.enabled=false")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertThrows(com.entloom.meta.contract.diagnostic.MetaDiagnosticException.class,
                    () -> context.getBean(MetadataLoader.class).load(emptyRequest()));
            });
    }

    @Test
    void 资源名独立于约定物理表名() {
        runner.withPropertyValues(entities(ResourceNamedEntity.class)).run(context -> {
            assertNull(context.getStartupFailure());
            DdlEntityMetadata ddl = context.getBean(MetadataLoader.class).load(emptyRequest()).get(0);
            assertEquals("resource_named", ddl.tableName());
            assertEquals("catalog-resource",
                context.getBean(EntityMetaRegistry.class).getEntityMeta(ResourceNamedEntity.class).getEntityName());
        });
    }

    @Test
    void 显式Ddl来源优先且空扫描结果不回退Meta来源() {
        runner.withPropertyValues(entities(DefaultEntity.class, AssignedEntity.class)).run(context -> {
            assertNull(context.getStartupFailure());
            MetadataLoader loader = context.getBean(MetadataLoader.class);
            assertEquals(2, loader.load(emptyRequest()).size());
            MetadataLoadRequest request = new MetadataLoadRequest(Collections.singletonList("com.entloom.missing"),
                java.util.Arrays.asList(AssignedEntity.class, AssignedEntity.class));
            assertEquals(1, loader.load(request).size());
            assertEquals(AssignedEntity.class.getName(), loader.load(request).get(0).entityClassName());
            assertTrue(loader.load(new MetadataLoadRequest(Collections.singletonList("com.entloom.missing"),
                Collections.emptyList())).isEmpty());
        });
    }

    @Test
    void 契约一致的联合主键允许建表且字段或列不一致必须失败() {
        runner.withPropertyValues(entities(AssignedCompositeEntity.class, MissingCompositeKeyEntity.class,
            MismatchedCompositeColumnEntity.class))
            .withPropertyValues("ent.loom.meta.defaults.id-policy=ASSIGNED")
            .run(context -> {
                assertNull(context.getStartupFailure());
                MetadataLoader loader = context.getBean(MetadataLoader.class);
                DdlEntityMetadata ddl = loader.load(new MetadataLoadRequest(Collections.emptyList(),
                    Collections.singletonList(AssignedCompositeEntity.class))).get(0);
                assertTrue(new MysqlCreateTableSqlBuilder().build(ddl, "")
                    .contains("PRIMARY KEY (`id`, `other_id`)"));
                for (Class<?> type : new Class<?>[] {MissingCompositeKeyEntity.class, MismatchedCompositeColumnEntity.class}) {
                    assertThrows(IllegalStateException.class, () -> loader.load(new MetadataLoadRequest(
                        Collections.emptyList(), Collections.singletonList(type))));
                }
            });
    }

    @Test
    void 独立Ddl默认配置支持自增及显式关闭() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(EntDdlAutoConfiguration.class))
            .withPropertyValues("ent.loom.ddl.defaults.generation-strategy=AUTO_INCREMENT")
            .run(context -> {
                MetadataLoadRequest request = new MetadataLoadRequest(Collections.emptyList(),
                    java.util.Arrays.asList(DdlOnlyEntity.class, DdlAssignedEntity.class));
                for (DdlEntityMetadata model : context.getBean(MetadataLoader.class).load(request)) {
                    assertEquals(model.entityClassName().equals(DdlOnlyEntity.class.getName())
                        ? GenerationStrategy.AUTO_INCREMENT : GenerationStrategy.NONE,
                        model.fields().get(0).generationStrategy());
                }
            });
    }

    @Test
    void 缺少Ddl运行时或关闭适配时使用原有装配() {
        runner.withClassLoader(new FilteredClassLoader("com.entloom.ddl.starter", "com.entloom.ddl.spring"))
            .withPropertyValues(entities(DefaultEntity.class))
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertFalse(context.containsBean("entLoomMetaDdlMetadataLoader"));
                assertNotNull(context.getBean(EntMetaParser.class));
            });
        runner.withPropertyValues(entities(DefaultEntity.class))
            .withPropertyValues("ent.loom.meta.ddl.enabled=false")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertFalse(context.getBean(MetadataLoader.class) instanceof MetaDdlMetadataLoader);
            });
    }

    @Test
    void 整数Java类型不能覆盖为非整数自增列且不支持序列() {
        for (Class<?> type : new Class<?>[] {InvalidColumnEntity.class, SequenceEntity.class, CompositeEntity.class}) {
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(EntLoomMetaAutoConfiguration.class,
                EntLoomMetaDdlAutoConfiguration.class, EntDdlAutoConfiguration.class))
                .withPropertyValues(entities(type))
                .withPropertyValues("ent.loom.meta.defaults.id-policy=DATABASE",
                    "ent.loom.meta.crud.enabled=false", "ent.loom.meta.doc.enabled=false")
                .run(context -> assertThrows(IllegalArgumentException.class, () -> {
                    DdlEntityMetadata model = context.getBean(MetadataLoader.class).load(emptyRequest()).get(0);
                    new MysqlCreateTableSqlBuilder().build(model, "");
                }));
        }
    }

    private MetadataLoadRequest emptyRequest() {
        return new MetadataLoadRequest(Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void 原生Crud解析也应识别Meta策略和Ddl覆盖() {
        com.entloom.crud.core.runtime.model.CrudRuntimeModel model =
            new com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser()
                .parse(java.util.Arrays.asList(DatabaseEntity.class, AssignedEntity.class,
                    NativeAssignedEntity.class, ApplicationEntity.class));
        assertEquals(EntityIdPolicy.GENERATED, model.getEntity(DatabaseEntity.class).getIdentity().getIdPolicy());
        assertEquals(EntityIdPolicy.EXPLICIT, model.getEntity(AssignedEntity.class).getIdentity().getIdPolicy());
        assertEquals(EntityIdPolicy.EXPLICIT, model.getEntity(NativeAssignedEntity.class).getIdentity().getIdPolicy());
        assertEquals(EntityIdPolicy.APPLICATION, model.getEntity(ApplicationEntity.class).getIdentity().getIdPolicy());
    }

    private String[] entities(Class<?>... classes) {
        String[] properties = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            properties[i] = "ent.loom.meta.entity-class-names[" + i + "]=" + classes[i].getName();
        }
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CrudProperties.class)
    @Import(CrudCommonConfiguration.class)
    static class CrudRegistryConfiguration {
        @Bean
        CrudIdPolicyDefaults crudIdPolicyDefaults(CrudProperties properties) {
            return new CrudIdPolicyDefaults(properties.getDefaults().getIdPolicy());
        }
    }

    @EntEntity
    static class DefaultEntity {
        Long id;
        String name;
    }

    @EntEntity
    static class AssignedEntity {
        @EntMetaId(policy = EntIdPolicy.ASSIGNED)
        Long id;
    }

    @EntEntity
    static class DatabaseEntity {
        @EntMetaId(policy = EntIdPolicy.DATABASE)
        Long id;
    }

    @EntEntity
    static class NativeAssignedEntity {
        @EntMetaId(policy = EntIdPolicy.DATABASE)
        @EntDdlField(generationStrategy = GenerationStrategy.NONE)
        Long id;
    }

    @EntEntity
    static class ApplicationEntity {
        @EntMetaId(generator = EntMetaId.IdGenerator.SNOWFLAKE)
        @EntDdlField(generationStrategy = GenerationStrategy.NONE)
        Long id;
    }

    @EntEntity
    @EntCrudEntity(idPolicy = CrudIdPolicy.EXPLICIT)
    static class ConflictingEntity {
        Long id;
    }

    @EntEntity
    static class StringIdEntity {
        String id;
    }

    @EntEntity(entity = "catalog-resource")
    static class ResourceNamedEntity {
        Long id;
    }

    @EntEntity
    @EntCrudEntity(idField = "otherId, id", idPolicy = CrudIdPolicy.COMPOSITE)
    static class AssignedCompositeEntity {
        Long id;
        @EntDdlField(primaryKey = com.entloom.base.common.OptionalBoolean.TRUE)
        Long otherId;
    }

    @EntEntity
    @EntCrudEntity(idField = "id,otherId", idPolicy = CrudIdPolicy.COMPOSITE)
    static class MissingCompositeKeyEntity {
        Long id;
        Long otherId;
    }

    @EntEntity
    @EntCrudEntity(idField = "id,otherId", idPolicy = CrudIdPolicy.COMPOSITE)
    static class MismatchedCompositeColumnEntity {
        Long id;
        @EntDdlField(primaryKey = com.entloom.base.common.OptionalBoolean.TRUE, column = "different_id")
        Long otherId;
    }

    @EntDdlEntity
    static class DdlOnlyEntity {
        Long id;
    }

    @EntDdlEntity
    static class DdlAssignedEntity {
        @EntDdlField(generationStrategy = GenerationStrategy.NONE)
        Long id;
    }

    @EntEntity
    static class InvalidColumnEntity {
        @EntDdlField(columnDefinition = "varchar(64)")
        Long id;
    }

    @EntEntity
    static class SequenceEntity {
        @EntDdlField(generationStrategy = GenerationStrategy.SEQUENCE)
        Long id;
    }

    @EntEntity
    static class CompositeEntity {
        Long id;
        @EntDdlField(primaryKey = com.entloom.base.common.OptionalBoolean.TRUE)
        Long otherId;
    }
}
