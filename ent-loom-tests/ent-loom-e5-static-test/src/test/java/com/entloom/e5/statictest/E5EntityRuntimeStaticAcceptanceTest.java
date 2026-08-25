package com.entloom.e5.statictest;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeFieldModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.core.MysqlCreateTableSqlBuilder;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.ddl.MetaDdlAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaMedia;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.MediaRole;
import com.entloom.meta.enums.role.NumberRole;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E5.1：一个无关系实体的静态 Runtime Model 组合验收。
 *
 * <p>本测试只验证公开构件和内存模型，不启动 Spring、不连接数据库、不发 HTTP。</p>
 */
class E5EntityRuntimeStaticAcceptanceTest {

    @Test
    @DisplayName("简单实体应同时形成 Meta、DDL、CRUD、DOC、UI Runtime Model")
    void shouldProjectOneSimpleEntityToAllRuntimeModels() {
        List<Class<?>> entityClasses = Collections.<Class<?>>singletonList(CustomerProfile.class);

        MetaDiagnosticResult<EntEntityDescriptor> metaResult =
            new ReflectiveEntMetaParser().parseWithDiagnostics(CustomerProfile.class);
        assertNoErrors("Meta", metaResult.diagnostics());
        EntEntityDescriptor meta = metaResult.value();
        Assertions.assertNotNull(meta);
        Assertions.assertEquals("customer_profile", meta.entityName());
        Assertions.assertEquals("客户档案", meta.label());
        Assertions.assertEquals(5, meta.fields().size());
        Assertions.assertTrue(meta.relations().isEmpty());
        Assertions.assertEquals(EntFieldKind.TEXT.name(), field(meta, "displayName").fieldKind());
        Assertions.assertEquals(EntFieldKind.NUMBER.name(), field(meta, "creditLimit").fieldKind());
        Assertions.assertEquals(EntFieldKind.DATETIME.name(), field(meta, "registeredAt").fieldKind());
        Assertions.assertEquals(EntFieldKind.MEDIA.name(), field(meta, "avatarUrl").fieldKind());

        MetaDdlAdapter ddlAdapter = new MetaDdlAdapter(entityClasses);
        assertNoErrors("DDL Adapter", ddlAdapter.diagnostics());
        Assertions.assertEquals(1, ddlAdapter.models().size());
        DdlEntityMetadata ddl = ddlAdapter.models().get(0);
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

        MetaCrudAdapter crudAdapter = new MetaCrudAdapter(entityClasses);
        assertNoErrors("CRUD Adapter", crudAdapter.diagnostics());
        CrudRuntimeModel crud = crudAdapter.runtimeModel();
        CrudRuntimeEntityModel crudEntity = crud.getEntity(CustomerProfile.class);
        Assertions.assertNotNull(crudEntity);
        Assertions.assertEquals("customer_profile", crudEntity.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("customer_profile", crudEntity.getTable());
        Assertions.assertEquals("id", crudEntity.getIdentity().getIdField());
        Assertions.assertEquals("id", crudEntity.getIdentity().getIdColumn());
        Assertions.assertEquals(5, crudEntity.getFields().size());
        Assertions.assertEquals("display_name", crudField(crudEntity, "displayName").getColumnName());
        Assertions.assertEquals(BigDecimal.class, crudField(crudEntity, "creditLimit").getJavaType());
        Assertions.assertEquals(LocalDateTime.class, crudField(crudEntity, "registeredAt").getJavaType());
        Assertions.assertTrue(crud.getRelations().isEmpty());

        MetaDocAdapter docAdapter = new MetaDocAdapter(new SnakeCaseDocMetaResolver(), entityClasses);
        assertNoErrors("DOC Adapter", docAdapter.diagnostics());
        Assertions.assertEquals(1, docAdapter.models().size());
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

        UiSchemaProvider uiProvider = entityCode -> meta.entityName().equals(entityCode) ? uiModel(meta) : null;
        UiEntityContract ui = uiProvider.resolve(meta.entityName());
        Assertions.assertNotNull(ui);
        Assertions.assertEquals(meta.entityName(), ui.entityCode());
        Assertions.assertEquals(meta.defaultLabelFields().get(0), ui.titleFieldName());
        Map<String, UiFieldContract> uiFields = indexUiFields(ui.fields());
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

    /**
     * E5.1 只组合公开 UI Contract，字段身份、展示名称和组件类型均来自已解析的 Meta 模型。
     */
    private UiEntityContract uiModel(EntEntityDescriptor meta) {
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
        return new UiEntityContract(
            meta.entityName(),
            titleFieldName(meta),
            uiFields
        );
    }

    private String titleFieldName(EntEntityDescriptor meta) {
        Assertions.assertFalse(meta.defaultLabelFields().isEmpty(), "Meta 实体必须提供默认标签字段");
        return meta.defaultLabelFields().get(0);
    }

    private UiFieldContract.UiComponentType uiComponentType(String fieldKind) {
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

    private Map<String, UiFieldContract> indexUiFields(List<UiFieldContract> fields) {
        Map<String, UiFieldContract> result = new LinkedHashMap<String, UiFieldContract>();
        for (UiFieldContract field : fields) {
            result.put(field.fieldName(), field);
        }
        return result;
    }

    private Set<String> fieldNames(EntEntityDescriptor entity) {
        Set<String> result = new LinkedHashSet<String>();
        for (EntFieldDescriptor field : entity.fields()) {
            result.add(field.fieldName());
        }
        return result;
    }

    private EntFieldDescriptor field(EntEntityDescriptor entity, String fieldName) {
        for (EntFieldDescriptor field : entity.fields()) {
            if (fieldName.equals(field.fieldName())) {
                return field;
            }
        }
        Assertions.fail("缺少 Meta 字段: " + fieldName);
        return null;
    }

    private DdlFieldMetadata ddlField(DdlEntityMetadata entity, String fieldName) {
        for (DdlFieldMetadata field : entity.fields()) {
            if (fieldName.equals(field.fieldName())) {
                return field;
            }
        }
        Assertions.fail("缺少 DDL 字段: " + fieldName);
        return null;
    }

    private CrudRuntimeFieldModel crudField(CrudRuntimeEntityModel entity, String fieldName) {
        CrudRuntimeFieldModel field = entity.getFields().get(fieldName);
        Assertions.assertNotNull(field, "缺少 CRUD 字段: " + fieldName);
        return field;
    }

    private DocFieldModel docField(DocEntityModel entity, String fieldName) {
        for (DocFieldModel field : entity.fields()) {
            if (fieldName.equals(field.property())) {
                return field;
            }
        }
        Assertions.fail("缺少 DOC 字段: " + fieldName);
        return null;
    }

    private void assertNoErrors(String modelName, List<MetaDiagnostic> diagnostics) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            Assertions.assertNotEquals(
                MetaDiagnosticLevel.ERROR,
                diagnostic.level(),
                modelName + " 产生错误诊断: " + diagnostic
            );
        }
    }

    /** 静态验收实体：只保留单实体字段，不声明关系。 */
    @EntEntity(
        entity = "customer_profile",
        label = "客户档案",
        description = "客户档案",
        service = "customer-service",
        defaultLabelFields = {"displayName"}
    )
    @EntIndex(name = "uk_customer_profile_display_name", fields = {"displayName"}, unique = true)
    @EntDbEntity(table = "customer_profile", comment = "客户档案")
    @EntDbIndex(
        name = "uk_customer_profile_display_name",
        fields = {"display_name"},
        unique = OptionalBoolean.TRUE
    )
    @EntCrudEntity(name = "customer_profile", table = "customer_profile", ownerService = "customer-service")
    @EntDocEntity(name = "客户档案", description = "客户档案")
    static final class CustomerProfile {
        /** 数据库主键。 */
        @EntField(value = EntFieldKind.ID, label = "标识", description = "主键", required = OptionalBoolean.TRUE)
        @EntDbField(
            column = "id",
            nullable = OptionalBoolean.FALSE,
            primaryKey = OptionalBoolean.TRUE,
            generationStrategy = GenerationStrategy.AUTO_INCREMENT,
            comment = "主键"
        )
        @EntDocField(name = "标识", description = "主键", required = OptionalBoolean.TRUE)
        private Long id;

        /** 客户展示名称。 */
        @EntField(
            value = EntFieldKind.TEXT,
            label = "显示名称",
            description = "客户展示名称",
            examples = {"张三"},
            required = OptionalBoolean.TRUE
        )
        @EntMetaText(maxLength = 64)
        @EntDbField(column = "display_name", length = 64, nullable = OptionalBoolean.FALSE, comment = "客户展示名称")
        @EntDocField(
            name = "显示名称",
            description = "客户展示名称",
            example = "张三",
            required = OptionalBoolean.TRUE,
            maxLength = 64
        )
        private String displayName;

        /** 客户信用额度。 */
        @EntField(
            value = EntFieldKind.NUMBER,
            label = "信用额度",
            description = "客户信用额度",
            examples = {"1000.00"},
            required = OptionalBoolean.TRUE
        )
        @EntMetaNumber(value = NumberRole.MONEY, precision = 10, scale = 2)
        @EntDbField(column = "credit_limit", precision = 10, scale = 2, nullable = OptionalBoolean.FALSE, comment = "客户信用额度")
        @EntDocField(
            name = "信用额度",
            description = "客户信用额度",
            example = "1000.00",
            required = OptionalBoolean.TRUE
        )
        private BigDecimal creditLimit;

        /** 客户注册时间。 */
        @EntField(
            value = EntFieldKind.DATETIME,
            label = "注册时间",
            description = "客户注册时间",
            required = OptionalBoolean.TRUE
        )
        @EntMetaDateTime(value = DateTimeRole.CREATED_TIME, encoding = EntMetaDateTime.TimeEncoding.ISO_LOCAL)
        @EntDbField(column = "registered_at", nullable = OptionalBoolean.FALSE, comment = "客户注册时间")
        @EntDocField(name = "注册时间", description = "客户注册时间", required = OptionalBoolean.TRUE)
        private LocalDateTime registeredAt;

        /** 客户头像地址，作为 UI 图片字段验收样本。 */
        @EntField(
            value = EntFieldKind.MEDIA,
            label = "头像",
            description = "客户头像地址",
            required = OptionalBoolean.FALSE
        )
        @EntMetaMedia(value = MediaRole.IMAGE, pathMode = EntMetaMedia.PathMode.ABSOLUTE_URL, accept = {"image/png", "image/jpeg"})
        @EntDbField(column = "avatar_url", length = 255, nullable = OptionalBoolean.TRUE, comment = "客户头像地址")
        @EntDocField(name = "头像", description = "客户头像地址", required = OptionalBoolean.FALSE)
        private String avatarUrl;
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
