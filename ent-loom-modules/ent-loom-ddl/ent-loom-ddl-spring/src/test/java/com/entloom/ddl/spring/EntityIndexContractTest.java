package com.entloom.ddl.spring;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 实体级索引的属性映射与输入校验。 */
class EntityIndexContractTest {
    @Test
    void shouldMapPropertiesInOrderAndPreserveNativeExpression() {
        DdlEntityMetadata entity = load(IndexedEntity.class);
        assertEquals(3, entity.indexes().size());
        assertEquals(Arrays.asList("account_code", "tenant_id"), entity.indexes().get(0).fields());
        assertTrue(entity.indexes().get(0).unique());
        assertEquals(Collections.singletonList("tenant_id"), entity.indexes().get(1).fields());
        assertTrue(entity.indexes().get(2).fields().isEmpty());
        assertEquals("lower(account_code)", entity.indexes().get(2).expression());
    }

    @Test
    void shouldRejectInvalidIndexDeclarations() {
        for (Class<?> type : Arrays.<Class<?>>asList(
                EmptyIndex.class, ConflictingIndex.class, UnknownProperty.class,
                PhysicalColumn.class, NonPersistentProperty.class, BlankProperty.class,
                DuplicateProperty.class)) {
            assertThrows(RuntimeException.class, () -> load(type), type.getSimpleName());
        }
    }

    @Test
    void 子集加载不受其他实体声明影响但全量校验仍拒绝漏扫描() {
        var declarations = new com.entloom.declaration.EntityDeclarations(
            new org.springframework.core.io.ClassPathResource("declarations/EntityIndexContractTest/IndexedEntity.yml"),
            new org.springframework.core.io.ByteArrayResource("ddl: {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                @Override public String getFilename() { return "OtherEntity.yml"; }
            });
        var loader = new SpringAnnotationMetadataLoader(null, new com.entloom.ddl.api.DdlGenerationDefaults(), declarations);
        var request = new com.entloom.ddl.api.MetadataLoadRequest(Collections.emptyList(), Collections.singletonList(IndexedEntity.class));
        assertEquals(3, loader.load(request).get(0).indexes().size());
        var error = assertThrows(IllegalArgumentException.class, () -> loader.validateDeclarations(request));
        assertTrue(error.getMessage().contains("OtherEntity"));
    }

    private DdlEntityMetadata load(Class<?> type) {
        return new SpringAnnotationMetadataLoader(null, new com.entloom.ddl.api.DdlGenerationDefaults(), new com.entloom.declaration.EntityDeclarations(new org.springframework.core.io.ClassPathResource("declarations/EntityIndexContractTest/" + type.getSimpleName() + ".yml"))).load(new MetadataLoadRequest(Collections.<String>emptyList(), Collections.<Class<?>>singletonList(type))).get(0);
    }

    private static class BaseEntity {
        Long id;
        Long tenantId;
        @EntDdlField(column = "account_code")
        String accountCode;
        @EntDdlField(persisted = OptionalBoolean.FALSE)
        String displayLabel;
    }

    @EntDdlEntity

    private static class IndexedEntity extends BaseEntity {
    }

    @EntDdlEntity

    private static class EmptyIndex extends BaseEntity {
    }

    @EntDdlEntity

    private static class ConflictingIndex extends BaseEntity {
    }

    @EntDdlEntity

    private static class UnknownProperty extends BaseEntity {
    }

    @EntDdlEntity

    private static class PhysicalColumn extends BaseEntity {
    }

    @EntDdlEntity

    private static class NonPersistentProperty extends BaseEntity {
    }

    @EntDdlEntity

    private static class BlankProperty extends BaseEntity {
    }

    @EntDdlEntity

    private static class DuplicateProperty extends BaseEntity {
    }
}
