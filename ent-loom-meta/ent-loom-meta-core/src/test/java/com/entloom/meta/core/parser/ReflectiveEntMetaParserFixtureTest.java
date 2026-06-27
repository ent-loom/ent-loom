package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.EnumRole;
import com.entloom.meta.enums.role.RefIdRole;
import com.entloom.meta.enums.role.TextRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReflectiveEntMetaParserFixtureTest {

    @Test
    void parser_fixture_should_cover_field_roles_relations_indexes_inference_and_diagnostics() {
        ReflectiveEntMetaParser parser = new ReflectiveEntMetaParser();

        MetaDiagnosticResult<EntEntityDescriptor> result = parser.parseWithDiagnostics(ParserFixtureEntity.class);

        Assertions.assertNotNull(result.value());
        Assertions.assertTrue(result.hasErrors());
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.INFERRED_VALUE_USED));
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND));
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.INDEX_FIELD_NOT_FOUND));

        EntEntityDescriptor descriptor = result.value();
        Assertions.assertEquals("parser_fixture", descriptor.entityName());
        Assertions.assertEquals(6, descriptor.fields().size());
        Assertions.assertEquals(2, descriptor.relations().size());
        Assertions.assertEquals(2, descriptor.indexes().size());

        Map<String, EntFieldDescriptor> fields = fieldsByName(descriptor);
        Assertions.assertEquals("ID", fields.get("id").role());
        Assertions.assertEquals("TEXT.GENERIC", fields.get("name").role());
        Assertions.assertEquals("ENUM.STATUS", fields.get("status").role());
        Assertions.assertEquals("DATETIME.CREATED_TIME", fields.get("createdAt").role());
        Assertions.assertEquals("REF_ID.TENANT", fields.get("ownerId").role());

        Assertions.assertEquals("SNOWFLAKE", constraints(fields.get("id")).get("id.generator"));
        Assertions.assertEquals("80", constraints(fields.get("name")).get("text.maxLength"));
        Assertions.assertEquals(ParserFixtureStatus.class.getName(), constraints(fields.get("status")).get("enum.class"));
        Assertions.assertEquals("STRING", constraints(fields.get("status")).get("enum.valueType"));
        Assertions.assertEquals("SINGLE", constraints(fields.get("status")).get("enum.cardinality"));
        Assertions.assertEquals("CREATED", constraints(fields.get("createdAt")).get("dateTime.autoFill"));
        Assertions.assertEquals("ISO_LOCAL", constraints(fields.get("createdAt")).get("dateTime.encoding"));
        Assertions.assertEquals("Asia/Shanghai", constraints(fields.get("createdAt")).get("dateTime.timezone"));

        EntRelationDescriptor inferredRelation = descriptor.relations().get(0);
        Assertions.assertEquals("ownerId", inferredRelation.sourceField());
        Assertions.assertEquals("owner", inferredRelation.targetEntity());
        Assertions.assertEquals("id", inferredRelation.targetField());
        Assertions.assertEquals(RelationOwnerSide.DECLARING_ENTITY, inferredRelation.ownerSide());
        Assertions.assertTrue(inferredRelation.sourceFieldInferred());

        EntRelationDescriptor brokenRelation = descriptor.relations().get(1);
        Assertions.assertEquals("missingReviewerId", brokenRelation.sourceField());
        Assertions.assertEquals("reviewer", brokenRelation.targetEntity());
        Assertions.assertFalse(brokenRelation.sourceFieldInferred());
    }

    @Test
    void parser_should_ignore_legacy_relation_naming_fixture() {
        EntEntityDescriptor descriptor = new ReflectiveEntMetaParser().parse(LegacyRelationNamingEntity.class);

        Assertions.assertTrue(descriptor.relations().isEmpty());
        Assertions.assertEquals(2, descriptor.fields().size());
        Assertions.assertEquals("REF_ID", fieldsByName(descriptor).get("ownerId").fieldKind());
    }

    @Test
    void parser_should_infer_ent_fields_for_entity_properties_without_field_annotation() {
        EntEntityDescriptor descriptor = new ReflectiveEntMetaParser().parse(InferredFieldsEntity.class);

        Map<String, EntFieldDescriptor> fields = fieldsByName(descriptor);
        Assertions.assertEquals(8, fields.size());
        Assertions.assertEquals("ID", fields.get("id").fieldKind());
        Assertions.assertEquals("REF_ID", fields.get("tenantId").fieldKind());
        Assertions.assertEquals("TEXT", fields.get("name").fieldKind());
        Assertions.assertEquals("ENUM", fields.get("status").fieldKind());
        Assertions.assertEquals("FLAG", fields.get("enabled").fieldKind());
        Assertions.assertEquals("DATETIME", fields.get("createdAt").fieldKind());
        Assertions.assertEquals("JSON_DOC", fields.get("profileJson").fieldKind());
        Assertions.assertEquals("MEDIA", fields.get("coverImageUrl").fieldKind());
    }

    @Test
    void parser_should_report_basic_semantic_diagnostics() {
        MetaDiagnosticResult<EntEntityDescriptor> result = new ReflectiveEntMetaParser()
            .parseWithDiagnostics(SemanticallyBrokenEntity.class);

        Assertions.assertTrue(result.hasErrors());
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.FIELD_KIND_META_MISMATCH));
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.INVALID_FIELD_CONSTRAINT));
        Assertions.assertTrue(codes(result.diagnostics()).contains(MetaDiagnosticCode.DUPLICATE_INDEX));
    }

    private Map<String, EntFieldDescriptor> fieldsByName(EntEntityDescriptor descriptor) {
        Map<String, EntFieldDescriptor> values = new LinkedHashMap<String, EntFieldDescriptor>();
        for (EntFieldDescriptor field : descriptor.fields()) {
            values.put(field.fieldName(), field);
        }
        return values;
    }

    private Map<String, String> constraints(EntFieldDescriptor descriptor) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (EntFieldConstraintDescriptor constraint : descriptor.constraints()) {
            values.put(constraint.name(), constraint.value());
        }
        return values;
    }

    private List<MetaDiagnosticCode> codes(List<MetaDiagnostic> diagnostics) {
        List<MetaDiagnosticCode> values = new ArrayList<MetaDiagnosticCode>();
        for (MetaDiagnostic diagnostic : diagnostics) {
            values.add(diagnostic.code());
        }
        return values;
    }

    private enum ParserFixtureStatus {
        DRAFT,
        ACTIVE
    }

    private enum InferredStatus {
        INIT,
        ACTIVE
    }

    @EntEntity(entity = "inferred_fields")
    private static final class InferredFieldsEntity {
        private Long id;
        private Long tenantId;
        private String name;
        private InferredStatus status;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private String profileJson;
        private String coverImageUrl;
    }

    @EntEntity(entity = "semantically_broken")
    @EntIndex(name = "idx_duplicate", fields = {"amount"})
    @EntIndex(name = "idx_duplicate", fields = {"name"})
    private static final class SemanticallyBrokenEntity {
        private Long id;

        @EntField(EntFieldKind.TEXT)
        @EntMetaNumber(precision = 4, scale = 6, min = "9", max = "1")
        private BigDecimal amount;

        @EntField(EntFieldKind.DATETIME)
        @EntMetaDateTime(timezone = "Invalid/Zone")
        private LocalDateTime createdAt;

        private String name;
    }

    @EntEntity(entity = "parser_fixture")
    @EntIndex(name = "idx_parser_fixture_name_status", fields = {"name", "status"})
    @EntIndex(name = "idx_parser_fixture_missing", fields = {"missingIndexField"})
    private static final class ParserFixtureEntity {
        @EntField(EntFieldKind.ID)
        @EntMetaId(generator = EntMetaId.IdGenerator.SNOWFLAKE)
        private Long id;

        @EntField(value = EntFieldKind.TEXT, required = OptionalBoolean.TRUE)
        @EntMetaText(value = TextRole.GENERIC, maxLength = 80)
        private String name;

        @EntField(EntFieldKind.ENUM)
        @EntMetaEnum(
            value = EnumRole.STATUS,
            enumClass = ParserFixtureStatus.class,
            valueType = EntMetaEnum.ValueType.STRING,
            cardinality = EntMetaEnum.Cardinality.SINGLE
        )
        private ParserFixtureStatus status;

        @EntField(EntFieldKind.DATETIME)
        @EntMetaDateTime(
            value = DateTimeRole.CREATED_TIME,
            autoFill = EntMetaDateTime.AutoFill.CREATED,
            encoding = EntMetaDateTime.TimeEncoding.ISO_LOCAL,
            timezone = "Asia/Shanghai"
        )
        private LocalDateTime createdAt;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(role = RefIdRole.TENANT, targetEntity = "owner")
        private Long ownerId;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(sourceField = "missingReviewerId", targetEntity = "reviewer")
        private Long reviewerId;
    }

    @EntEntity(entity = "legacy_relation_naming")
    private static final class LegacyRelationNamingEntity {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @LegacyRelation(relationEntityEn = "owner", refEntity = "owner")
        private Long ownerId;
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface LegacyRelation {
        String relationEntityEn() default "";

        String refEntity() default "";
    }
}
