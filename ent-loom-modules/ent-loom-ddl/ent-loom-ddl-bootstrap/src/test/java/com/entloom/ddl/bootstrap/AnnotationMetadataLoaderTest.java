package com.entloom.ddl.bootstrap;

import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 无 Spring 注解元数据加载合同测试。
 */
class AnnotationMetadataLoaderTest {
    @Test
    void should_exclude_complex_properties_without_explicit_annotation() {
        DdlEntityMetadata model = new AnnotationMetadataLoader().load(new MetadataLoadRequest(
            Collections.<String>emptyList(), Collections.<Class<?>>singletonList(Aggregate.class))).get(0);
        assertEquals(1, model.fields().stream().filter(DdlFieldMetadata::persisted).count());
        assertEquals("id", model.fields().get(0).fieldName());
        assertThrows(IllegalArgumentException.class, () -> new AnnotationMetadataLoader().load(new MetadataLoadRequest(
            Collections.<String>emptyList(), Collections.<Class<?>>singletonList(Unsupported.class))));
    }

    @EntDdlEntity
    private static class Aggregate {
        Long id;
        List<AccountEntity> items;
        AccountEntity account;
        @EntDdlField(persisted = OptionalBoolean.FALSE)
        String displayName;
    }

    @EntDdlEntity
    private static class Unsupported {
        Long id;
        @EntDdlField(persisted = OptionalBoolean.TRUE)
        AccountEntity account;
    }

    @Test
    @DisplayName("包装类型 id 自动主键时默认生成非空列")
    void shouldMakeInferredIdNonNullable() {
        List<DdlEntityMetadata> entities = new AnnotationMetadataLoader().load(
                new MetadataLoadRequest(Collections.<String>emptyList(),
                        Collections.<Class<?>>singletonList(AccountEntity.class)));

        DdlFieldMetadata id = entities.get(0).fields().get(0);

        assertTrue(id.primaryKey());
        assertFalse(id.nullable());
        assertFalse(entities.get(0).fields().get(1).nullable());
    }

    @EntDdlEntity(table = "account")
    private static final class AccountEntity {
        private Long id;
        private String nickname;
    }
}
