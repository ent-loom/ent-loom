package com.entloom.meta.core.parser;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.model.MetaEntityDefaults;
import com.entloom.meta.enums.EntIdPolicy;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetaIdPolicyTest {
    private final EntMetaParser parser = new ReflectiveEntMetaParser(Collections.emptyList(),
        new MetaEntityDefaults(null, EntIdPolicy.DATABASE));

    @Test
    void 默认策略只用于主键并记录来源() {
        EntFieldDescriptor id = parser.parse(DefaultEntity.class).fields().get(0);
        assertEquals(EntIdPolicy.DATABASE, id.idPolicy());
        assertEquals(MetaValueSource.BUSINESS_DEFAULT_CONFIG,
            id.sourcedValue(MetaDescriptorProperties.ID_POLICY).source());
        assertFalse(id.sourcedValue(MetaDescriptorProperties.ID_POLICY).explicit());
        assertEquals(EntIdPolicy.UNSET, parser.parse(DefaultEntity.class).fields().get(1).idPolicy());
    }

    @Test
    void 显式策略和应用生成器覆盖全局默认() {
        assertEquals(EntIdPolicy.ASSIGNED, parser.parse(AssignedEntity.class).fields().get(0).idPolicy());
        assertEquals(EntIdPolicy.APPLICATION, parser.parse(ApplicationEntity.class).fields().get(0).idPolicy());
        assertEquals(EntIdPolicy.UNSET, new ReflectiveEntMetaParser().parse(DefaultEntity.class).fields().get(0).idPolicy());
    }

    @Test
    void 数据库生成与应用生成器冲突应失败() {
        assertThrows(com.entloom.meta.contract.diagnostic.MetaDiagnosticException.class,
            () -> parser.parse(InvalidEntity.class));
    }

    @EntEntity
    static class DefaultEntity {
        Long id;
        Long customerId;
    }

    @EntEntity
    static class AssignedEntity {
        @EntMetaId(policy = EntIdPolicy.ASSIGNED)
        Long id;
    }

    @EntEntity
    static class ApplicationEntity {
        @EntMetaId(generator = EntMetaId.IdGenerator.SNOWFLAKE)
        Long id;
    }

    @EntEntity
    static class InvalidEntity {
        @EntMetaId(policy = EntIdPolicy.DATABASE, generator = EntMetaId.IdGenerator.SNOWFLAKE)
        Long id;
    }
}
