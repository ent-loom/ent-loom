package com.entloom.e5.statictest;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.base.util.reflect.EntityProperties;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.meta.adapter.crud.MetaCrudAdapter;
import com.entloom.meta.adapter.ddl.MetaDdlAdapter;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import com.entloom.doc.core.spi.DocEntityMetaResolver;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.ddl.core.MysqlCreateTableSqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.descriptor.EntFieldDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.RelationCardinality;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 业务属性保留、普通列排除和跨模块一致性的静态验收。 */
class EntityPropertyMappingAcceptanceTest {
    @Test
    void should_keep_business_properties_but_share_column_eligibility() {
        ReflectiveEntMetaParser parser = new ReflectiveEntMetaParser();
        Map<String, EntFieldDescriptor> properties = parser.parse(Aggregate.class).fields().stream()
            .collect(Collectors.toMap(EntFieldDescriptor::fieldName, field -> field));
        assertEquals("订单明细", properties.get("items").label());
        assertEquals("STRUCTURED", properties.get("items").fieldKind());
        assertSame(Item.class, properties.get("items").elementType());
        assertSame(Item.class, properties.get("attributes").elementType());
        assertNull(properties.get("rawItems").elementType());
        for (String name : Arrays.asList("items", "unnamedItems", "customer", "attributes", "rawItems", "array",
            "displayName", "cache", "customNumber")) {
            assertFalse(properties.get(name).persisted(), name);
        }
        assertEquals(MetaValueSource.META_EXPLICIT,
            properties.get("displayName").sourcedValue(MetaDescriptorProperties.PERSISTED).source());
        assertEquals(MetaValueSource.INFERRED,
            properties.get("items").sourcedValue(MetaDescriptorProperties.PERSISTED).source());

        EntityMeta nativeMeta = nativeMeta(Aggregate.class);
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(Aggregate.class, Item.class));
        EntityMeta meta = adapter.runtimeModel().getEntity(Aggregate.class).toEntityMeta();
        Set<String> expected = Set.of("id", "customerId", "amount", "binary");
        assertEquals(expected, nativeMeta.getAllowedFields());
        assertEquals(expected, meta.getAllowedFields());
        DdlEntityMetadata ddl = new MetaDdlAdapter(Collections.<Class<?>>singletonList(Aggregate.class)).models().get(0);
        assertEquals(expected, ddl.fields().stream().filter(DdlFieldMetadata::persisted)
            .map(DdlFieldMetadata::fieldName).collect(Collectors.toSet()));

        assertEquals("items", adapter.runtimeModel().relationEdges().get(0).getRelationField());
        assertEquals("id", adapter.runtimeModel().relationEdges().get(0).getFromField());
        assertEquals("orderId", adapter.runtimeModel().relationEdges().get(0).getToField());
        assertEquals(Set.of("items", "unnamedItems"), adapter.runtimeModel().relationEdges().stream()
            .map(edge -> edge.getRelationField()).collect(Collectors.toSet()));

        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(
            new CrudRuntimeModelBackedEntityMetaRegistry(adapter.runtimeModel()));
        for (String name : Arrays.asList("items", "displayName")) {
            assertThrows(ValidationException.class, () -> validator.validateQuerySpec(QuerySpec.<Aggregate>builder()
                .rootType(Aggregate.class).resultType(Aggregate.class).op(QueryOperation.LIST)
                .filters(Collections.singletonList(new QueryFilter(name, FilterOperator.EQ, "value"))).build()));
            assertThrows(ValidationException.class, () -> validator.validateQuerySpec(QuerySpec.<Aggregate>builder()
                .rootType(Aggregate.class).resultType(Aggregate.class).op(QueryOperation.LIST)
                .sorts(Collections.singletonList(new QuerySort(name, SortDirection.ASC))).build()));
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("amount", BigDecimal.TEN);
        payload.put("items", Collections.singletonList(new Item()));
        payload.put("displayName", "临时名称");
        Map<String, Object> values = new DefaultCommandPayloadBinder().bindFieldMap(payload, meta);
        assertFalse(values.containsKey("items"));
        assertFalse(values.containsKey("displayName"));
        assertEquals(BigDecimal.TEN, values.get("amount"));

        Map<String, Object> doc = new MetaDocAdapter(new DocEntityMetaResolver() {
            @Override
            public String resolveTableName(Class<?> type, String configured) {
                return type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public String resolveColumn(Class<?> type, String property) {
                return property;
            }
        },
            Arrays.<Class<?>>asList(Aggregate.class, Item.class)).buildOne(Aggregate.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docFields = (List<Map<String, Object>>) doc.get("fields");
        assertTrue(docFields.stream().anyMatch(field -> "items".equals(field.get("property"))));
    }

    @Test
    void should_use_generated_ddl_for_single_table_writes_and_manual_assembly() {
        DdlEntityMetadata ddl = new MetaDdlAdapter(Collections.<Class<?>>singletonList(Aggregate.class)).models().get(0);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:property-mapping;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop table if exists aggregate");
        jdbc.execute(new MysqlCreateTableSqlBuilder().build(ddl, ""));
        CrudRuntimeModelBackedEntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new MetaCrudAdapter(Arrays.<Class<?>>asList(Aggregate.class, Item.class)).runtimeModel());
        registry.validateOrThrow();
        EntityDao<Aggregate, Long> dao = new JdbcEntityDaoFactory(registry,
            new JdbcGuardedSqlExecutor(jdbc, new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(registry),
                new SqlParameterLimiter()), new SqlExecutionLogger()))
            .scoped(EntityType.of(Aggregate.class, Long.class), EntityAccessScope.unrestricted());
        Aggregate order = new Aggregate();
        order.id = 1L;
        order.customerId = 2L;
        order.amount = BigDecimal.TEN;
        order.binary = new byte[] {1, 2};
        order.items = Collections.singletonList(new Item());
        order.displayName = "手动展示值";
        dao.insert(order);
        Aggregate loaded = dao.findById(1L).get();
        assertNull(loaded.items);
        assertNull(loaded.displayName);
        assertArrayEquals(order.binary, loaded.binary);
        loaded.items = order.items;
        loaded.displayName = order.displayName;
        loaded.amount = BigDecimal.ONE;
        assertEquals(1, dao.update(loaded));
        assertEquals(0, BigDecimal.ONE.compareTo(dao.findById(1L).get().amount));
        assertEquals(1, jdbc.queryForObject("select count(*) from aggregate", Integer.class));
        assertEquals(4, jdbc.queryForMap("select * from aggregate").size());
    }

    @Test
    void should_reject_unsupported_explicit_storage_and_non_column_indexes() {
        assertTrue(new ReflectiveEntMetaParser().parseWithDiagnostics(UnsupportedStorage.class).diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code() == MetaDiagnosticCode.UNSUPPORTED_COLUMN_MAPPING));
        assertThrows(RuntimeException.class, () -> nativeMeta(UnsupportedStorage.class));
        assertThrows(RuntimeException.class, () -> new MetaDdlAdapter(Collections.<Class<?>>singletonList(UnsupportedStorage.class)));
        assertDoesNotThrow(() -> new ReflectiveEntMetaParser().parse(InvalidIndex.class));
        assertThrows(RuntimeException.class, () -> new MetaDdlAdapter(Collections.<Class<?>>singletonList(InvalidIndex.class)));
        assertThrows(RuntimeException.class, () -> new MetaDdlAdapter(Collections.<Class<?>>singletonList(InvalidNativeStorage.class)));
    }

    @Test
    void should_validate_indexes_after_native_persistence_overrides() {
        MetaDdlAdapter adapter = new MetaDdlAdapter(Collections.<Class<?>>singletonList(NativeIndexOverride.class));
        DdlEntityMetadata ddl = adapter.models().get(0);
        assertTrue(ddl.fields().stream().filter(field -> field.fieldName().equals("name"))
            .findFirst().get().persisted());
        assertEquals(Collections.singletonList("stored_name"), ddl.indexes().get(0).fields());
        assertTrue(adapter.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code() == MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT));
        assertThrows(RuntimeException.class,
            () -> new MetaDdlAdapter(Collections.<Class<?>>singletonList(NativeIndexExclusion.class)));
    }

    @Test
    void should_round_trip_special_single_column_types() {
        DdlEntityMetadata ddl = new MetaDdlAdapter(Collections.<Class<?>>singletonList(SpecialValues.class)).models().get(0);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:special-property-mapping;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop table if exists special_values");
        jdbc.execute(new MysqlCreateTableSqlBuilder().build(ddl, ""));
        CrudRuntimeModelBackedEntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new MetaCrudAdapter(Collections.<Class<?>>singletonList(SpecialValues.class)).runtimeModel());
        registry.validateOrThrow();
        EntityDao<SpecialValues, Long> dao = new JdbcEntityDaoFactory(registry,
            new JdbcGuardedSqlExecutor(jdbc, new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(registry),
                new SqlParameterLimiter()), new SqlExecutionLogger()))
            .scoped(EntityType.of(SpecialValues.class, Long.class), EntityAccessScope.unrestricted());
        SpecialValues values = new DefaultCommandPayloadBinder().bindEntity(Map.of(
            "id", "1", "marker", "A", "largeNumber", "123456789",
            "eventTime", "2026-09-27T08:30:00Z", "clockTime", "08:30"),
            SpecialValues.class, registry.getEntityMeta(SpecialValues.class));
        dao.insert(values);
        SpecialValues loaded = dao.findById(1L).get();
        assertEquals(values.marker, loaded.marker);
        assertEquals(values.largeNumber, loaded.largeNumber);
        assertEquals(values.eventTime, loaded.eventTime);
        assertEquals(values.clockTime, loaded.clockTime);
        values.id = 2L;
        values.clockTime = java.time.LocalTime.parse("08:30:00.123456");
        assertThrows(ValidationException.class, () -> dao.insert(values));
        assertFalse(dao.findById(2L).isPresent());
        values.id = 1L;
        assertThrows(ValidationException.class, () -> dao.update(values));
        assertEquals(loaded.clockTime, dao.findById(1L).get().clockTime);
    }

    @Test
    void should_reject_non_column_identity_logic_delete_and_scope_fields() {
        for (Class<?> type : Arrays.<Class<?>>asList(InvalidIdentity.class, InvalidLogicDelete.class, InvalidScope.class)) {
            assertThrows(RuntimeException.class, () -> new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(type))).validateOrThrow());
            assertThrows(RuntimeException.class, () -> new CrudRuntimeModelBackedEntityMetaRegistry(
                new MetaCrudAdapter(Collections.<Class<?>>singletonList(type)).runtimeModel()).validateOrThrow());
        }
    }

    @Test
    void should_prefer_child_property_and_keep_unknown_generics_unresolved() {
        List<java.lang.reflect.Field> fields = EntityProperties.fields(Child.class);
        assertEquals(2, fields.size());
        assertSame(Child.class, fields.get(0).getDeclaringClass());
        assertNull(EntityProperties.describe(fields.get(1)).elementType());
        assertFalse(EntityProperties.describe(fields.get(0)).persisted());
    }

    private EntityMeta nativeMeta(Class<?> type) {
        return new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(type)).getEntity(type).toEntityMeta();
    }

    @EntEntity
    @EntCrudEntity
    @EntDdlEntity
    public static class Aggregate {
        public Long id;
        public Long customerId;
        public BigDecimal amount;
        public byte[] binary;
        @EntField("订单明细")
        @EntRelation(targetEntity = "item", sourceField = "id", targetField = "orderId",
            cardinality = RelationCardinality.ONE_TO_MANY)
        public List<Item> items;
        @EntRelation(targetEntity = "item", sourceField = "id", targetField = "orderId",
            cardinality = RelationCardinality.ONE_TO_MANY)
        public List<Item> unnamedItems;
        public Item customer;
        public Map<String, Item> attributes;
        public List<?> rawItems;
        public Item[] array;
        @EntField(value = "临时名称", persisted = OptionalBoolean.FALSE)
        public String displayName;
        public transient String cache;
        public Number customNumber;
    }

    @EntEntity
    public static class Item {
        public Long id;
        public Long orderId;
    }

    @EntEntity
    public static class SpecialValues {
        public Long id;
        @EntField(persisted = OptionalBoolean.TRUE)
        public Character marker;
        public java.math.BigInteger largeNumber;
        public java.time.Instant eventTime;
        public java.time.LocalTime clockTime;
    }

    @EntEntity
    @EntCrudEntity
    static class UnsupportedStorage {
        Long id;
        @EntField(persisted = OptionalBoolean.TRUE)
        Map<String, Object> attributes;
    }

    @EntEntity
    @EntIndex(fields = "items")
    static class InvalidIndex {
        Long id;
        List<Item> items;
    }

    @EntEntity
    @EntIndex(fields = "name")
    static class NativeIndexOverride {
        Long id;
        @EntField(persisted = OptionalBoolean.FALSE)
        @EntDdlField(persisted = OptionalBoolean.TRUE, column = "stored_name")
        String name;
    }

    @EntEntity
    @EntIndex(fields = "name")
    static class NativeIndexExclusion {
        Long id;
        @EntDdlField(persisted = OptionalBoolean.FALSE)
        String name;
    }

    @EntDdlEntity
    static class InvalidNativeStorage {
        Long id;
        @EntDdlField(persisted = OptionalBoolean.TRUE)
        Item item;
    }

    @EntEntity
    @EntCrudEntity
    static class InvalidIdentity {
        @EntField(persisted = OptionalBoolean.FALSE)
        Long id;
        String name;
    }

    @EntEntity
    @EntCrudEntity(logicDeleteField = "deleted", logicDeleteNotDeletedValue = "0", logicDeleteDeletedValue = "1")
    static class InvalidLogicDelete {
        Long id;
        @EntField(persisted = OptionalBoolean.FALSE)
        Integer deleted;
    }

    @EntEntity
    @EntCrudEntity(scopeFields = "tenantId")
    static class InvalidScope {
        Long id;
        @EntField(persisted = OptionalBoolean.FALSE)
        Long tenantId;
    }

    static class Parent<T> {
        Long name;
        List<T> values;
    }

    static class Child extends Parent<Item> {
        Item name;
    }
}
