package com.entloom.meta.core.parser;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.base.util.value.TypedValueType;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldConstraintDescriptor;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.contract.value.MetaValueState;
import com.entloom.meta.contract.value.SourcedValue;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.NumberRole;
import com.entloom.meta.enums.role.RefIdRole;
import com.entloom.meta.enums.role.TextRole;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReflectiveEntMetaParserDescriptorContractTest {

    @Test
    void parser_should_fill_minimal_descriptor_contract_for_crud_and_doc_projection() {
        EntEntityDescriptor descriptor = new ReflectiveEntMetaParser().parse(ContractEntity.class);

        Assertions.assertSame(ContractEntity.class, descriptor.entityClass());
        Assertions.assertEquals("contract_entity", descriptor.entityName());
        Assertions.assertEquals("contract-service", descriptor.serviceName());
        Assertions.assertEquals("Contract Entity", descriptor.label());
        Assertions.assertEquals("Contract descriptor fixture", descriptor.description());
        Assertions.assertEquals("name", descriptor.defaultLabelFields().get(0));
        Assertions.assertEquals(Long.valueOf(1000L), descriptor.plannedVolume());
        assertSource(descriptor.sourcedValue(MetaDescriptorProperties.ENTITY_NAME), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(descriptor.sourcedValue(MetaDescriptorProperties.ENTITY_CLASS), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);

        Assertions.assertEquals(4, descriptor.fields().size());
        EntFieldDescriptor id = descriptor.fields().get(0);
        Assertions.assertEquals("id", id.fieldName());
        Assertions.assertSame(Long.class, id.javaType());
        Assertions.assertEquals("ID", id.fieldKind());
        Assertions.assertEquals("ID", id.role());
        Assertions.assertEquals("SNOWFLAKE", constraints(id).get("id.generator"));
        assertSource(id.sourcedValue(MetaDescriptorProperties.FIELD_KIND), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(id.sourcedValue(MetaDescriptorProperties.JAVA_TYPE), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);
        assertSource(id.sourcedValue(MetaDescriptorProperties.ROLE), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);

        EntFieldDescriptor ownerId = descriptor.fields().get(1);
        Assertions.assertEquals("ownerId", ownerId.fieldName());
        Assertions.assertEquals("REF_ID.TENANT", ownerId.role());

        EntFieldDescriptor name = descriptor.fields().get(2);
        Assertions.assertEquals("name", name.fieldName());
        Assertions.assertSame(String.class, name.javaType());
        Assertions.assertEquals("TEXT", name.fieldKind());
        Assertions.assertEquals("TEXT.SECRET", name.role());
        Assertions.assertEquals("Name", name.label());
        Assertions.assertEquals("Displayed name", name.description());
        Assertions.assertEquals("Alice", name.examples().get(0));
        Assertions.assertEquals(Boolean.TRUE, name.required());
        Assertions.assertEquals(Boolean.TRUE, name.readOnly());
        Assertions.assertEquals("new-user", name.createDefaultValue());
        Assertions.assertEquals(TypedValueType.STRING, name.createDefaultValueType());
        Assertions.assertEquals("new-user", name.typedCreateDefaultValue());
        Assertions.assertEquals("64", constraints(name).get("text.maxLength"));
        Assertions.assertEquals("[A-Za-z]+", constraints(name).get("text.pattern"));
        Assertions.assertEquals("PARTIAL", constraints(name).get("text.masking"));
        assertSource(name.sourcedValue(MetaDescriptorProperties.LABEL), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(name.sourcedValue(MetaDescriptorProperties.REQUIRED), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(name.sourcedValue(MetaDescriptorProperties.CREATE_DEFAULT_VALUE), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(name.sourcedValue(MetaDescriptorProperties.CREATE_DEFAULT_VALUE_TYPE), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);

        EntFieldDescriptor amount = descriptor.fields().get(3);
        Assertions.assertSame(BigDecimal.class, amount.javaType());
        Assertions.assertEquals("NUMBER.MONEY", amount.role());
        Assertions.assertEquals(TypedValueType.DECIMAL, amount.createDefaultValueType());
        Assertions.assertEquals(new BigDecimal("12.34"), amount.typedCreateDefaultValue());
        Assertions.assertEquals("10", constraints(amount).get("number.precision"));
        Assertions.assertEquals("2", constraints(amount).get("number.scale"));
        Assertions.assertEquals("0", constraints(amount).get("number.min"));
        Assertions.assertEquals("9999.99", constraints(amount).get("number.max"));
        Assertions.assertEquals("CNY", constraints(amount).get("number.unit"));

        Assertions.assertEquals(1, descriptor.relations().size());
        EntRelationDescriptor relation = descriptor.relations().get(0);
        Assertions.assertEquals("ownerId", relation.sourceField());
        Assertions.assertEquals("account-service", relation.targetService());
        Assertions.assertEquals("account", relation.targetEntity());
        Assertions.assertEquals("id", relation.targetField());
        Assertions.assertEquals(RelationOwnerSide.DECLARING_ENTITY, relation.ownerSide());
        Assertions.assertEquals(RelationResolutionStatus.PARTIALLY_RESOLVED, relation.resolutionStatus());
        Assertions.assertTrue(relation.sourceFieldInferred());
        assertSource(relation.sourcedValue(MetaDescriptorProperties.SOURCE_FIELD), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.TARGET_ENTITY), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.TARGET_FIELD), MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, MetaValueState.UNKNOWN, false);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.CARDINALITY), MetaValueSource.DEFAULT_OR_EXPLICIT_UNKNOWN, MetaValueState.UNKNOWN, false);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.OWNER_SIDE), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.RESOLUTION_STATUS), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);
        assertSource(relation.sourcedValue(MetaDescriptorProperties.SOURCE_FIELD_INFERRED), MetaValueSource.INFERRED, MetaValueState.INFERRED, false);

        Assertions.assertEquals(1, descriptor.indexes().size());
        Assertions.assertEquals("uk_contract_entity_name", descriptor.indexes().get(0).name());
        Assertions.assertEquals("name", descriptor.indexes().get(0).fields().get(0));
        Assertions.assertTrue(descriptor.indexes().get(0).unique());
        assertSource(descriptor.indexes().get(0).sourcedValue(MetaDescriptorProperties.INDEX_NAME), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
        assertSource(descriptor.indexes().get(0).sourcedValue(MetaDescriptorProperties.UNIQUE), MetaValueSource.META_EXPLICIT, MetaValueState.EXPLICIT, true);
    }

    @Test
    void parser_should_return_structured_diagnostics_and_fail_fast_by_policy() {
        ReflectiveEntMetaParser parser = new ReflectiveEntMetaParser();

        MetaDiagnosticResult<EntEntityDescriptor> result = parser.parseWithDiagnostics(BrokenContractEntity.class);

        Assertions.assertNotNull(result.value());
        Assertions.assertTrue(result.hasErrors());
        List<MetaDiagnosticCode> codes = diagnosticCodes(result.diagnostics());
        Assertions.assertTrue(codes.contains(MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND));
        Assertions.assertTrue(codes.contains(MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND));
        Assertions.assertTrue(codes.contains(MetaDiagnosticCode.INDEX_FIELD_NOT_FOUND));

        MetaDiagnostic sourceFieldDiagnostic = firstDiagnostic(result.diagnostics(), MetaDiagnosticCode.RELATION_SOURCE_FIELD_NOT_FOUND);
        Assertions.assertEquals(MetaDiagnosticLevel.ERROR, sourceFieldDiagnostic.level());
        Assertions.assertEquals("broken_contract_entity", sourceFieldDiagnostic.entity());
        Assertions.assertEquals("missingOwnerId", sourceFieldDiagnostic.field());
        Assertions.assertEquals(MetaDescriptorProperties.SOURCE_FIELD, sourceFieldDiagnostic.property());

        MetaDiagnosticException exception = Assertions.assertThrows(
            MetaDiagnosticException.class,
            () -> parser.parse(BrokenContractEntity.class)
        );
        Assertions.assertFalse(exception.diagnostics().isEmpty());
        Assertions.assertEquals(MetaDiagnosticLevel.ERROR, exception.diagnostics().get(0).level());
    }

    private Map<String, String> constraints(EntFieldDescriptor descriptor) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (EntFieldConstraintDescriptor constraint : descriptor.constraints()) {
            values.put(constraint.name(), constraint.value());
        }
        return values;
    }

    private void assertSource(SourcedValue<?> value, MetaValueSource source, MetaValueState state, boolean explicit) {
        Assertions.assertNotNull(value);
        Assertions.assertEquals(source, value.source());
        Assertions.assertEquals(state, value.state());
        Assertions.assertEquals(explicit, value.explicit());
    }

    private List<MetaDiagnosticCode> diagnosticCodes(List<MetaDiagnostic> diagnostics) {
        List<MetaDiagnosticCode> codes = new ArrayList<MetaDiagnosticCode>();
        for (MetaDiagnostic diagnostic : diagnostics) {
            codes.add(diagnostic.code());
        }
        return codes;
    }

    private MetaDiagnostic firstDiagnostic(List<MetaDiagnostic> diagnostics, MetaDiagnosticCode code) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code) {
                return diagnostic;
            }
        }
        Assertions.fail("Missing diagnostic: " + code);
        return null;
    }

    @EntEntity(
        entity = "contract_entity",
        service = "contract-service",
        label = "Contract Entity",
        description = "Contract descriptor fixture",
        defaultLabelFields = {"name"},
        plannedVolume = 1000L
    )
    @EntIndex(name = "uk_contract_entity_name", fields = {"name"}, unique = true)
    private static final class ContractEntity {
        @EntField(EntFieldKind.ID)
        @EntMetaId(generator = EntMetaId.IdGenerator.SNOWFLAKE)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(role = RefIdRole.TENANT, targetService = "account-service", targetEntity = "account")
        private Long ownerId;

        @EntField(
            value = EntFieldKind.TEXT,
            label = "Name",
            description = "Displayed name",
            examples = {"Alice"},
            createDefaultValue = "new-user",
            required = OptionalBoolean.TRUE,
            readOnly = OptionalBoolean.TRUE
        )
        @EntMetaText(value = TextRole.SECRET, maxLength = 64, pattern = "[A-Za-z]+", masking = EntMetaText.Masking.PARTIAL)
        private String name;

        @EntField(value = EntFieldKind.NUMBER, createDefaultValue = "12.34")
        @EntMetaNumber(value = NumberRole.MONEY, precision = 10, scale = 2, min = "0", max = "9999.99", unit = "CNY")
        private BigDecimal amount;
    }

    @EntEntity(entity = "broken_contract_entity")
    @EntIndex(name = "idx_missing", fields = {"missingIndexField"})
    private static final class BrokenContractEntity {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(sourceField = "missingOwnerId")
        private Long ownerId;
    }
}
