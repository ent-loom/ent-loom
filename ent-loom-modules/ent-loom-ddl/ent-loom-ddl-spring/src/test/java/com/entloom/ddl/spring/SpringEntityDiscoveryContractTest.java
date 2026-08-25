package com.entloom.ddl.spring;

import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.annotations.EntDbField;
import com.entloom.ddl.annotations.EntDbIndex;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.spring.discoveryfixtures.AlphaEntity;
import com.entloom.ddl.spring.discoveryfixtures.ZetaEntity;
import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.NamingStrategy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring 实体、字段和索引解析合同测试。
 */
class SpringEntityDiscoveryContractTest {
    private static final String FIXTURE_PACKAGE = "com.entloom.ddl.spring.discoveryfixtures";

    @Test
    @DisplayName("Spring 解析器读取实体、字段和索引公开合同")
    void shouldLoadEntityFieldAndIndexMetadata() {
        List<DdlEntityMetadata> entities = new SpringAnnotationMetadataLoader(null).load(
                new MetadataLoadRequest(Collections.<String>emptyList(),
                        Collections.<Class<?>>singletonList(OrderEntity.class)));

        assertEquals(1, entities.size());
        DdlEntityMetadata entity = entities.get(0);
        assertEquals(OrderEntity.class.getName(), entity.entityClassName());
        assertEquals("biz", entity.schema());
        assertEquals("ddl_order", entity.tableName());
        assertEquals("订单实体", entity.comment());
        assertEquals(DdlTableSize.MEDIUM, entity.tableSize());

        DdlFieldMetadata id = entity.fields().get(0);
        assertTrue(id.primaryKey());
        assertFalse(id.nullable());
        assertEquals(GenerationStrategy.AUTO_INCREMENT, id.generationStrategy());

        DdlFieldMetadata orderNumber = entity.fields().get(1);
        assertEquals("order_no", orderNumber.columnName());
        assertFalse(orderNumber.nullable());
        assertTrue(orderNumber.unique());
        assertEquals(32, orderNumber.length());
        assertEquals("订单号", orderNumber.comment());
        assertEquals("old_order_no", orderNumber.renameFrom());

        DdlFieldMetadata amount = entity.fields().get(2);
        assertEquals(18, amount.precision());
        assertEquals(2, amount.scale());
        assertEquals("0", amount.defaultValue());
        assertFalse(entity.fields().get(3).persisted());

        assertEquals(2, entity.indexes().size());
        DdlIndexMetadata classIndex = entity.indexes().get(0);
        assertEquals("uk_order_no", classIndex.name());
        assertEquals(Collections.singletonList("order_no"), classIndex.fields());
        assertTrue(classIndex.unique());
        DdlIndexMetadata fieldIndex = entity.indexes().get(1);
        assertEquals("idx_amount", fieldIndex.name());
        assertEquals(Collections.singletonList("amount"), fieldIndex.fields());
    }

    @Test
    @DisplayName("Spring 包扫描处理空输入、重复包并返回稳定顺序")
    void shouldScanEmptyAndDuplicatePackagesInStableOrder() {
        SpringPackageEntityClassResolver resolver = new SpringPackageEntityClassResolver(
                getClass().getClassLoader());

        assertTrue(resolver.resolve(Collections.<String>emptyList()).isEmpty());
        assertEquals(Arrays.asList(AlphaEntity.class, ZetaEntity.class),
                resolver.resolve(Arrays.asList(FIXTURE_PACKAGE, " ", FIXTURE_PACKAGE)));
        assertTrue(resolver.resolve(Collections.singletonList("com.entloom.ddl.spring.missing")).isEmpty());
    }

    @Test
    @DisplayName("Spring 解析器合并显式类与扫描类时去重并按类名排序")
    void shouldDeduplicateAndSortExplicitAndScannedClasses() {
        List<DdlEntityMetadata> entities = new SpringAnnotationMetadataLoader(
                new SpringPackageEntityClassResolver(getClass().getClassLoader())).load(
                new MetadataLoadRequest(
                        Collections.singletonList(FIXTURE_PACKAGE),
                        Arrays.<Class<?>>asList(ZetaEntity.class, AlphaEntity.class, ZetaEntity.class)));

        assertEquals(Arrays.asList(AlphaEntity.class.getName(), ZetaEntity.class.getName()),
                entities.stream().map(DdlEntityMetadata::entityClassName).collect(Collectors.toList()));
    }

    @EntDbEntity(table = "ddl_order", schema = "biz", comment = "订单实体",
            size = DdlTableSize.MEDIUM, namingStrategy = NamingStrategy.SNAKE_CASE)
    @EntDbIndex(name = "uk_order_no", fields = {"order_no"}, unique = com.entloom.base.common.OptionalBoolean.TRUE)
    private static final class OrderEntity {
        /**
         * 订单主键。
         */
        @EntDbField(generationStrategy = GenerationStrategy.AUTO_INCREMENT)
        private Long id;

        /**
         * 订单号。
         */
        @EntDbField(column = "order_no", nullable = com.entloom.base.common.OptionalBoolean.FALSE,
                unique = com.entloom.base.common.OptionalBoolean.TRUE, length = 32,
                comment = "订单号", renameFrom = "old_order_no")
        private String orderNumber;

        /**
         * 订单金额。
         */
        @EntDbField(precision = 18, scale = 2, defaultValue = "0")
        @EntDbIndex(name = "idx_amount")
        private java.math.BigDecimal amount;

        /**
         * 仅用于验证不持久化字段。
         */
        @EntDbField(persisted = com.entloom.base.common.OptionalBoolean.FALSE)
        private String transientLabel;
    }
}
