package com.entloom.meta.adapter.ddl;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.contract.diagnostic.DefaultMetaDiagnosticPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.enums.EntFieldKind;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaDdlAdapterP0AcceptanceTest {

    @Test
    void 应支持MetaOnly并把通用语义投影为DDL模型() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(Collections.<Class<?>>singletonList(MetaOnlyAccount.class));

        DdlEntityMetadata model = adapter.models().get(0);

        Assertions.assertEquals("meta_only_account", model.tableName());
        Assertions.assertEquals(DdlTableSize.UNSET, model.tableSize());
        Assertions.assertEquals("id", model.fields().get(0).columnName());
        Assertions.assertTrue(model.fields().get(0).primaryKey());
        Assertions.assertEquals(GenerationStrategy.AUTO_INCREMENT, model.fields().get(0).generationStrategy());
        DdlFieldMetadata displayName = field(model, "displayName");
        Assertions.assertEquals("display_name", displayName.columnName());
        Assertions.assertEquals(64, displayName.length());
        Assertions.assertFalse(displayName.nullable());
        DdlFieldMetadata amount = field(model, "amount");
        Assertions.assertEquals(10, amount.precision());
        Assertions.assertEquals(2, amount.scale());
        Assertions.assertEquals(1, model.indexes().size());
        Assertions.assertEquals("uk_meta_account_display_name", model.indexes().get(0).name());
        Assertions.assertEquals("display_name", model.indexes().get(0).fields().get(0));
    }

    @Test
    void 应支持DDLOnly并保留DDL专属属性() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(Collections.<Class<?>>singletonList(DdlOnlyAccount.class));

        DdlEntityMetadata model = adapter.models().get(0);

        Assertions.assertEquals("native_account", model.tableName());
        Assertions.assertEquals("account_schema", model.schema());
        Assertions.assertEquals("Native account", model.comment());
        Assertions.assertEquals(DdlTableSize.MEDIUM, model.tableSize());
        DdlFieldMetadata id = field(model, "id");
        Assertions.assertEquals("account_id", id.columnName());
        Assertions.assertTrue(id.primaryKey());
        Assertions.assertEquals(GenerationStrategy.AUTO_INCREMENT, id.generationStrategy());
        DdlFieldMetadata code = field(model, "code");
        Assertions.assertEquals(32, code.length());
        Assertions.assertEquals("'new'", code.defaultValue());
        Assertions.assertEquals("old_code", code.renameFrom());
        Assertions.assertEquals(1, model.indexes().size());
        Assertions.assertTrue(model.indexes().get(0).unique());
    }

    @Test
    void 应以DDLExplicitOverrideMeta并在边界暴露冲突诊断() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(
            Arrays.<Class<?>>asList(OverrideAccount.class, MetaOnlyAccount.class, OverrideAccount.class),
            new com.entloom.meta.core.parser.ReflectiveEntMetaParser(),
            DefaultMetaDiagnosticPolicy.lenient()
        );

        DdlEntityMetadata model = find(adapter.models(), OverrideAccount.class.getName());
        Assertions.assertEquals("native_override_account", model.tableName());
        Assertions.assertEquals("native_display", field(model, "displayName").columnName());
        Assertions.assertEquals(24, field(model, "displayName").length());
        Assertions.assertTrue(hasDiagnostic(adapter.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, "tableName"));
        Assertions.assertTrue(hasDiagnostic(adapter.diagnostics(), MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT, "nullable"));
        Assertions.assertEquals(2, adapter.models().size());
    }

    @Test
    void 空输入应返回空模型且结果稳定排序() {
        Assertions.assertTrue(new MetaDdlAdapter(null).models().isEmpty());
        MetaDdlAdapter adapter = new MetaDdlAdapter(Arrays.<Class<?>>asList(MetaOnlyAccount.class, DdlOnlyAccount.class));
        Assertions.assertEquals(DdlOnlyAccount.class.getName(), adapter.models().get(0).entityClassName());
        Assertions.assertEquals(MetaOnlyAccount.class.getName(), adapter.models().get(1).entityClassName());
    }

    @Test
    void MetaFirst与等价DDL声明应生成一致的字段和索引模型() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(
            Arrays.<Class<?>>asList(MetaOnlyAccount.class, EquivalentDdlAccount.class)
        );

        DdlEntityMetadata metaModel = find(adapter.models(), MetaOnlyAccount.class.getName());
        DdlEntityMetadata nativeModel = find(adapter.models(), EquivalentDdlAccount.class.getName());

        Assertions.assertEquals(metaModel.tableName(), nativeModel.tableName());
        Assertions.assertEquals(metaModel.fields().size(), nativeModel.fields().size());
        for (int i = 0; i < metaModel.fields().size(); i++) {
            DdlFieldMetadata metaField = metaModel.fields().get(i);
            DdlFieldMetadata nativeField = nativeModel.fields().get(i);
            Assertions.assertEquals(metaField.fieldName(), nativeField.fieldName());
            Assertions.assertEquals(metaField.columnName(), nativeField.columnName());
            Assertions.assertEquals(metaField.javaType(), nativeField.javaType());
            Assertions.assertEquals(metaField.nullable(), nativeField.nullable());
            Assertions.assertEquals(metaField.primaryKey(), nativeField.primaryKey());
            Assertions.assertEquals(metaField.length(), nativeField.length());
            Assertions.assertEquals(metaField.precision(), nativeField.precision());
            Assertions.assertEquals(metaField.scale(), nativeField.scale());
            Assertions.assertEquals(metaField.generationStrategy(), nativeField.generationStrategy());
        }
        Assertions.assertEquals(metaModel.indexes(), nativeModel.indexes());
    }

    private DdlFieldMetadata field(DdlEntityMetadata model, String fieldName) {
        for (DdlFieldMetadata field : model.fields()) {
            if (fieldName.equals(field.fieldName())) {
                return field;
            }
        }
        Assertions.fail("缺少字段: " + fieldName);
        return null;
    }

    private DdlEntityMetadata find(List<DdlEntityMetadata> models, String className) {
        for (DdlEntityMetadata model : models) {
            if (className.equals(model.entityClassName())) {
                return model;
            }
        }
        Assertions.fail("缺少实体: " + className);
        return null;
    }

    private boolean hasDiagnostic(Iterable<MetaDiagnostic> diagnostics, MetaDiagnosticCode code, String property) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code && property.equals(diagnostic.property())) {
                return true;
            }
        }
        return false;
    }

    @EntEntity(entity = "meta_only_account")
    @EntIndex(name = "uk_meta_account_display_name", fields = {"displayName"}, unique = true)
    private static final class MetaOnlyAccount {
        @EntField(EntFieldKind.ID)
        @EntMetaId(generator = EntMetaId.IdGenerator.AUTO)
        private Long id;

        @EntField(value = EntFieldKind.TEXT, required = OptionalBoolean.TRUE, description = "账户名称")
        @EntMetaText(maxLength = 64)
        private String displayName;

        @EntField(EntFieldKind.NUMBER)
        @EntMetaNumber(precision = 10, scale = 2)
        private BigDecimal amount;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "tenant")
        private Long tenantId;
    }

    @EntDbEntity(table = "native_account", schema = "account_schema", comment = "Native account", size = DdlTableSize.MEDIUM)
    @EntDbIndex(name = "uk_native_account_code", fields = {"code"}, unique = OptionalBoolean.TRUE)
    private static final class DdlOnlyAccount {
        @EntDbField(column = "account_id", primaryKey = OptionalBoolean.TRUE, nullable = OptionalBoolean.FALSE,
            generationStrategy = GenerationStrategy.AUTO_INCREMENT)
        private Long id;

        @EntDbField(length = 32, nullable = OptionalBoolean.FALSE, defaultValue = "'new'", renameFrom = "old_code")
        private String code;

        private transient String ignored;
    }

    @EntEntity(entity = "meta_override_account", description = "Meta description")
    @EntDbEntity(table = "native_override_account")
    private static final class OverrideAccount {
        @EntField(EntFieldKind.ID)
        @EntDbField(column = "account_id", primaryKey = OptionalBoolean.TRUE, nullable = OptionalBoolean.FALSE)
        private Long id;

        @EntField(value = EntFieldKind.TEXT, required = OptionalBoolean.TRUE)
        @EntMetaText(maxLength = 80)
        @EntDbField(column = "native_display", length = 24, nullable = OptionalBoolean.TRUE)
        private String displayName;
    }

    @EntDbEntity(table = "meta_only_account")
    @EntDbIndex(name = "uk_meta_account_display_name", fields = {"display_name"}, unique = OptionalBoolean.TRUE)
    private static final class EquivalentDdlAccount {
        @EntDbField(primaryKey = OptionalBoolean.TRUE, nullable = OptionalBoolean.FALSE,
            generationStrategy = GenerationStrategy.AUTO_INCREMENT)
        private Long id;

        @EntDbField(column = "display_name", length = 64, nullable = OptionalBoolean.FALSE)
        private String displayName;

        @EntDbField(precision = 10, scale = 2)
        private BigDecimal amount;

        private Long tenantId;
    }
}
