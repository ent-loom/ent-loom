package com.entloom.e5.statictest;

import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeFieldModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.core.MysqlCreateTableSqlBuilder;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.e5.statictest.fixture.CustomerProfile;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.ddl.MetaDdlAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.ui.core.UiEntityContract;
import com.entloom.ui.core.UiFieldContract;
import com.entloom.ui.core.UiSchemaProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E5.1：一个无关系实体的静态 Runtime Model 组合验收。
 *
 * <p>本测试只验证公开构件和内存模型，不启动 Spring、不连接数据库、不发 HTTP。</p>
 */
class E5EntityRuntimeStaticAcceptanceTest {
    private static final List<Class<?>> ENTITY_CLASSES = Collections.<Class<?>>singletonList(CustomerProfile.class);

    @Test
    @DisplayName("CustomerProfile 应形成正确的 Meta 描述")
    void shouldBuildMetaDescriptor() {
        EntEntityDescriptor meta = meta();

        Assertions.assertEquals("customer_profile", meta.entityName());
        Assertions.assertEquals("客户档案", meta.label());
        Assertions.assertEquals(5, meta.fields().size());
        Assertions.assertTrue(meta.relations().isEmpty());
        Assertions.assertEquals(EntFieldKind.TEXT.name(), field(meta, "displayName").fieldKind());
        Assertions.assertEquals(EntFieldKind.NUMBER.name(), field(meta, "creditLimit").fieldKind());
        Assertions.assertEquals(EntFieldKind.DATETIME.name(), field(meta, "registeredAt").fieldKind());
        Assertions.assertEquals(EntFieldKind.MEDIA.name(), field(meta, "avatarUrl").fieldKind());
    }

    @Test
    @DisplayName("CustomerProfile 应形成正确的 DDL 模型和建表 SQL")
    void shouldBuildDdlModelAndCreateTableSql() {
        DdlEntityMetadata ddl = ddl();

        Assertions.assertEquals("", ddl.schema());
        Assertions.assertEquals("customer_profile", ddl.tableName());
        Assertions.assertEquals(5, ddl.fields().size());
        DdlFieldMetadata id = ddlField(ddl, "id");
        Assertions.assertFalse(id.nullable());
        Assertions.assertTrue(id.primaryKey());
        Assertions.assertEquals(GenerationStrategy.AUTO_INCREMENT, id.generationStrategy());
        Assertions.assertEquals(64, ddlField(ddl, "displayName").length());
        Assertions.assertEquals(10, ddlField(ddl, "creditLimit").precision());
        Assertions.assertEquals(2, ddlField(ddl, "creditLimit").scale());
        Assertions.assertEquals("registered_at", ddlField(ddl, "registeredAt").columnName());
        Assertions.assertEquals("avatar_url", ddlField(ddl, "avatarUrl").columnName());
        Assertions.assertEquals(1, ddl.indexes().size());
        Assertions.assertEquals("display_name", ddl.indexes().get(0).fields().get(0));

        String createTableSql = new MysqlCreateTableSqlBuilder().build(ddl, "static_acceptance");
        Assertions.assertTrue(createTableSql.contains("CREATE TABLE IF NOT EXISTS `static_acceptance`.`customer_profile`"));
        Assertions.assertTrue(createTableSql.contains("`id` bigint NOT NULL AUTO_INCREMENT"));
        Assertions.assertTrue(createTableSql.contains("`display_name` varchar(64) NOT NULL"));
        Assertions.assertTrue(createTableSql.contains("`credit_limit` decimal(10,2) NOT NULL"));
        Assertions.assertTrue(createTableSql.contains("`registered_at` datetime NOT NULL"));
        Assertions.assertTrue(createTableSql.contains("`avatar_url` varchar(255) NULL"));
        Assertions.assertTrue(createTableSql.contains("PRIMARY KEY (`id`)"));
        Assertions.assertTrue(createTableSql.contains("UNIQUE KEY `uk_customer_profile_display_name` (`display_name`)"));
    }

    @Test
    @DisplayName("CustomerProfile 应形成正确的 CRUD Runtime Model")
    void shouldBuildCrudRuntimeModel() {
        CrudRuntimeModel crud = crud();
        CrudRuntimeEntityModel crudEntity = requireNotNull(crud.getEntity(CustomerProfile.class), "缺少 CRUD 实体: CustomerProfile");

        Assertions.assertEquals("customer_profile", crudEntity.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("customer_profile", crudEntity.getTable());
        Assertions.assertEquals("id", crudEntity.getIdentity().getIdField());
        Assertions.assertEquals("id", crudEntity.getIdentity().getIdColumn());
        Assertions.assertEquals(5, crudEntity.getFields().size());
        Assertions.assertEquals("display_name", crudField(crudEntity, "displayName").getColumnName());
        Assertions.assertEquals(BigDecimal.class, crudField(crudEntity, "creditLimit").getJavaType());
        Assertions.assertEquals(LocalDateTime.class, crudField(crudEntity, "registeredAt").getJavaType());
        Assertions.assertTrue(crud.getRelations().isEmpty());
    }

    @Test
    @DisplayName("CustomerProfile 应形成正确的 DOC 模型")
    void shouldBuildDocModel() {
        MetaDocAdapter docAdapter = docAdapter();
        DocEntityModel doc = docAdapter.models().get(0);

        Assertions.assertEquals("customer_profile", doc.resourceCode().value());
        Assertions.assertEquals("客户档案", doc.entityName().value());
        Assertions.assertEquals("customer_profile", doc.tableName().value());
        Assertions.assertEquals(5, doc.fields().size());
        Assertions.assertTrue(doc.relations().isEmpty());
        Assertions.assertEquals("显示名称", docField(doc, "displayName").name().value());
        Assertions.assertEquals("display_name", docField(doc, "displayName").column().value());
        Assertions.assertEquals(EntFieldKind.MEDIA.name(), docField(doc, "avatarUrl").fieldKind().value());
        Map<String, Object> docOutput = docAdapter.buildOne(CustomerProfile.class);
        Assertions.assertEquals("customer_profile", docOutput.get("resourceCode"));
        Assertions.assertEquals(5, ((List<?>) docOutput.get("fields")).size());
    }

    @Test
    @DisplayName("CustomerProfile 的 UI 合同应与 Meta、DDL、CRUD、DOC 投影保持一致")
    void shouldKeepRuntimeModelsConsistent() {
        EntEntityDescriptor meta = meta();
        DdlEntityMetadata ddl = ddl();
        CrudRuntimeEntityModel crudEntity = requireNotNull(crud().getEntity(CustomerProfile.class), "缺少 CRUD 实体: CustomerProfile");
        DocEntityModel doc = docAdapter().models().get(0);
        UiSchemaProvider uiProvider = entityCode -> meta.entityName().equals(entityCode) ? uiModel(meta) : null;
        UiEntityContract ui = requireNotNull(uiProvider.resolve(meta.entityName()), "缺少 UI 实体合同: customer_profile");
        Map<String, UiFieldContract> uiFields = indexUiFields(ui.fields());

        Assertions.assertEquals(meta.entityName(), ui.entityCode());
        Assertions.assertEquals(meta.defaultLabelFields().get(0), ui.titleFieldName());
        Assertions.assertEquals(5, uiFields.size());
        Assertions.assertEquals(fieldNames(meta), uiFields.keySet());
        Assertions.assertEquals(fieldNames(meta), crudEntity.getFields().keySet());
        Assertions.assertEquals("标识", uiFields.get("id").fieldLabel());
        Assertions.assertFalse(uiFields.get("id").visibleInList());
        Assertions.assertFalse(uiFields.get("id").visibleInForm());
        Assertions.assertEquals(UiFieldContract.UiComponentType.TEXT, uiFields.get("displayName").componentType());
        Assertions.assertEquals(UiFieldContract.UiComponentType.NUMBER, uiFields.get("creditLimit").componentType());
        Assertions.assertEquals(UiFieldContract.UiComponentType.DATE_TIME, uiFields.get("registeredAt").componentType());
        Assertions.assertEquals(UiFieldContract.UiComponentType.IMAGE, uiFields.get("avatarUrl").componentType());
        Assertions.assertEquals(ddlField(ddl, "displayName").columnName(), crudField(crudEntity, "displayName").getColumnName());
        Assertions.assertEquals(crudField(crudEntity, "displayName").getColumnName(), docField(doc, "displayName").column().value());
    }

    private static EntEntityDescriptor meta() {
        MetaDiagnosticResult<EntEntityDescriptor> result = new ReflectiveEntMetaParser().parseWithDiagnostics(CustomerProfile.class);
        assertNoErrors("Meta", result.diagnostics());
        return requireNotNull(result.value(), "Meta 未生成实体描述: CustomerProfile");
    }

    private static DdlEntityMetadata ddl() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(ENTITY_CLASSES);
        assertNoErrors("DDL Adapter", adapter.diagnostics());
        Assertions.assertEquals(1, adapter.models().size());
        return adapter.models().get(0);
    }

    private static CrudRuntimeModel crud() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(ENTITY_CLASSES);
        assertNoErrors("CRUD Adapter", adapter.diagnostics());
        return adapter.runtimeModel();
    }

    private static MetaDocAdapter docAdapter() {
        MetaDocAdapter adapter = new MetaDocAdapter(new SnakeCaseDocMetaResolver(), ENTITY_CLASSES);
        assertNoErrors("DOC Adapter", adapter.diagnostics());
        Assertions.assertEquals(1, adapter.models().size());
        return adapter;
    }

    /** E5.1 只组合公开 UI Contract，字段身份、展示名称和组件类型均来自已解析的 Meta 模型。 */
    private static UiEntityContract uiModel(EntEntityDescriptor meta) {
        List<UiFieldContract> uiFields = new ArrayList<UiFieldContract>();
        for (EntFieldDescriptor field : meta.fields()) {
            boolean identity = EntFieldKind.ID.name().equals(field.fieldKind());
            uiFields.add(new UiFieldContract(
                field.fieldName(),
                field.label(),
                uiComponentType(field.fieldKind()),
                !identity,
                !identity
            ));
        }
        return new UiEntityContract(meta.entityName(), titleFieldName(meta), uiFields);
    }

    private static String titleFieldName(EntEntityDescriptor meta) {
        Assertions.assertFalse(meta.defaultLabelFields().isEmpty(), "Meta 实体必须提供默认标签字段");
        return meta.defaultLabelFields().get(0);
    }

    private static UiFieldContract.UiComponentType uiComponentType(String fieldKind) {
        if (EntFieldKind.ID.name().equals(fieldKind) || EntFieldKind.NUMBER.name().equals(fieldKind)) {
            return UiFieldContract.UiComponentType.NUMBER;
        }
        if (EntFieldKind.TEXT.name().equals(fieldKind)) {
            return UiFieldContract.UiComponentType.TEXT;
        }
        if (EntFieldKind.DATETIME.name().equals(fieldKind)) {
            return UiFieldContract.UiComponentType.DATE_TIME;
        }
        if (EntFieldKind.MEDIA.name().equals(fieldKind)) {
            return UiFieldContract.UiComponentType.IMAGE;
        }
        throw new IllegalArgumentException("E5.1 未定义字段类型的 UI 映射: " + fieldKind);
    }

    private static Map<String, UiFieldContract> indexUiFields(List<UiFieldContract> fields) {
        Map<String, UiFieldContract> result = new LinkedHashMap<String, UiFieldContract>();
        for (UiFieldContract field : fields) {
            result.put(field.fieldName(), field);
        }
        return result;
    }

    private static Set<String> fieldNames(EntEntityDescriptor entity) {
        Set<String> result = new LinkedHashSet<String>();
        for (EntFieldDescriptor field : entity.fields()) {
            result.add(field.fieldName());
        }
        return result;
    }

    private static EntFieldDescriptor field(EntEntityDescriptor entity, String fieldName) {
        return requireField(entity.fields(), fieldName, EntFieldDescriptor::fieldName, "Meta");
    }

    private static DdlFieldMetadata ddlField(DdlEntityMetadata entity, String fieldName) {
        return requireField(entity.fields(), fieldName, DdlFieldMetadata::fieldName, "DDL");
    }

    private static CrudRuntimeFieldModel crudField(CrudRuntimeEntityModel entity, String fieldName) {
        return requireNotNull(entity.getFields().get(fieldName), "缺少 CRUD 字段: " + fieldName);
    }

    private static DocFieldModel docField(DocEntityModel entity, String fieldName) {
        return requireField(entity.fields(), fieldName, DocFieldModel::property, "DOC");
    }

    private static <T> T requireField(Iterable<T> fields,
                                      String fieldName,
                                      Function<T, String> fieldNameResolver,
                                      String modelName) {
        for (T field : fields) {
            if (fieldName.equals(fieldNameResolver.apply(field))) {
                return field;
            }
        }
        return requireNotNull(null, "缺少 " + modelName + " 字段: " + fieldName);
    }

    private static <T> T requireNotNull(T value, String message) {
        Assertions.assertNotNull(value, message);
        return value;
    }

    private static void assertNoErrors(String modelName, List<MetaDiagnostic> diagnostics) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            Assertions.assertNotEquals(
                MetaDiagnosticLevel.ERROR,
                diagnostic.level(),
                modelName + " 产生错误诊断: " + diagnostic
            );
        }
    }

    private static final class SnakeCaseDocMetaResolver implements DocEntityMetaResolver {
        @Override
        public String resolveTableName(Class<?> entityClass, String configuredTableName) {
            return isBlank(configuredTableName) ? "customer_profile" : configuredTableName;
        }

        @Override
        public String resolveColumn(Class<?> entityClass, String property) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < property.length(); i++) {
                char current = property.charAt(i);
                if (Character.isUpperCase(current) && i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            }
            return result.toString();
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
