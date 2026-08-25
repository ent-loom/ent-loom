package com.entloom.ddl.bootstrap;

import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无 Spring 注解元数据加载合同测试。
 */
class AnnotationMetadataLoaderTest {

    @Test
    @DisplayName("包装类型 id 自动主键时默认生成非空列")
    void shouldMakeInferredIdNonNullable() {
        List<DdlEntityMetadata> entities = new AnnotationMetadataLoader().load(
                new MetadataLoadRequest(Collections.<String>emptyList(),
                        Collections.<Class<?>>singletonList(AccountEntity.class)));

        DdlFieldMetadata id = entities.get(0).fields().get(0);

        assertTrue(id.primaryKey());
        assertFalse(id.nullable());
    }

    @EntDbEntity(table = "account")
    private static final class AccountEntity {
        private Long id;
        private String nickname;
    }
}
