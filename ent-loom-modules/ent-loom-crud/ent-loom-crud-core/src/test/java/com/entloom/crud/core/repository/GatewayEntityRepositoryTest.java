package com.entloom.crud.core.repository;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayEntityRepositoryTest {
    private RecordingQueryGateway queryGateway;
    private RecordingCommandGateway commandGateway;
    private RecordingTransactionExecutor transactionExecutor;
    private EntityRepository<TestEntity, Integer> repository;

    @BeforeEach
    void setUp() {
        queryGateway = new RecordingQueryGateway();
        commandGateway = new RecordingCommandGateway();
        transactionExecutor = new RecordingTransactionExecutor();
        repository = new DefaultEntityRepositoryFactory(
            queryGateway,
            commandGateway,
            metaRegistry(),
            transactionExecutor
        ).repository(TestEntity.class, Integer.class);
    }

    @Test
    void should_translate_typed_queries_to_query_gateway() {
        TestEntity entity = new TestEntity(7, "测试");
        queryGateway.single = entity;

        Assertions.assertSame(entity, repository.findById(7).orElse(null));
        Assertions.assertEquals(QueryOperation.FIND_ONE, queryGateway.lastSpec.getOp());
        Assertions.assertEquals(TestEntity.class, queryGateway.lastSpec.getRootType());
        Assertions.assertEquals("id", queryGateway.lastSpec.getFilters().get(0).getField());
        Assertions.assertEquals(FilterOperator.EQ, queryGateway.lastSpec.getFilters().get(0).getOperator());

        queryGateway.total = 23L;
        long count = repository.count(EntityQuery.<TestEntity>builder().eq("name", "测试").build());
        Assertions.assertEquals(23L, count);
        Assertions.assertEquals(1, queryGateway.lastSpec.getPage().getLimit());
    }

    @Test
    void should_translate_single_and_conditional_writes_to_command_gateway() {
        Integer id = repository.insert(new TestEntity(null, "新增"));
        Assertions.assertEquals(11, id);
        Assertions.assertEquals(CommandOperation.CREATE, commandGateway.lastSpec.getOp());

        int rows = repository.update(
            EntityQuery.<TestEntity>builder().eq("name", "旧名称").build(),
            EntityPatch.<TestEntity>builder().set("name", "新名称").build()
        );
        Assertions.assertEquals(2, rows);
        Assertions.assertEquals(CommandOperation.UPDATE, commandGateway.lastSpec.getOp());
        Assertions.assertEquals("name", commandGateway.lastSpec.getTargetFilters().get(0).getField());

        Assertions.assertThrows(
            ValidationException.class,
            () -> repository.delete(EntityQuery.<TestEntity>empty())
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> repository.delete(EntityQuery.<TestEntity>builder().like("name", "旧").build())
        );
    }

    @Test
    void batch_writes_should_use_single_transaction() {
        List<Integer> ids = repository.insertBatch(Arrays.asList(
            new TestEntity(null, "甲"),
            new TestEntity(null, "乙")
        ));

        Assertions.assertEquals(Arrays.asList(101, 102), ids);
        Assertions.assertEquals(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, transactionExecutor.lastPolicy);
        Assertions.assertEquals(CommandOperation.CREATE_BATCH, commandGateway.lastSpec.getOp());
        Assertions.assertEquals(2, ((BatchCommand<?>) commandGateway.lastSpec.getPayload()).getItems().size());
    }

    @Test
    void empty_batches_should_not_open_a_transaction_or_call_gateway() {
        Assertions.assertTrue(repository.insertBatch(null).isEmpty());
        Assertions.assertEquals(0, repository.updateBatch(null));
        Assertions.assertEquals(0, repository.deleteBatchByIds(Collections.<Integer>emptyList()));
        Assertions.assertEquals(0, repository.saveBatch(Collections.<TestEntity>emptyList()).size());
        Assertions.assertNull(transactionExecutor.lastPolicy);
        Assertions.assertNull(commandGateway.lastSpec);
    }

    @Test
    void invalid_batch_input_should_fail_before_opening_a_transaction() {
        Assertions.assertThrows(
            ValidationException.class,
            () -> repository.insertBatch(Arrays.asList(new TestEntity(null, "有效"), null))
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> repository.updateBatch(Arrays.asList(
                new EntityUpdate<TestEntity, Integer>(null, EntityPatch.<TestEntity>builder().set("name", "无效").build())
            ))
        );
        Assertions.assertThrows(
            ValidationException.class,
            () -> repository.deleteBatchByIds(Arrays.asList(1, null))
        );
        Assertions.assertNull(transactionExecutor.lastPolicy);
        Assertions.assertNull(commandGateway.lastSpec);
    }

    @Test
    void save_batch_should_keep_input_order_and_child_operations() {
        queryGateway.single = new TestEntity(12, "已存在");

        List<TestEntity> saved = repository.saveBatch(Arrays.asList(
            new TestEntity(12, "更新"),
            new TestEntity(null, "新增")
        ));

        Assertions.assertEquals(2, saved.size());
        Assertions.assertEquals(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, transactionExecutor.lastPolicy);
        BatchCommand<?> batch = (BatchCommand<?>) commandGateway.lastSpec.getPayload();
        Assertions.assertEquals(CommandOperation.UPDATE, batch.getItems().get(0).getOp());
        Assertions.assertEquals(CommandOperation.CREATE, batch.getItems().get(1).getOp());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void invalid_runtime_id_type_should_be_reported_as_validation_error() {
        EntityRepository rawRepository = repository;

        Assertions.assertThrows(ValidationException.class, () -> rawRepository.findById("not-a-number"));
    }

    @Test
    void save_should_reload_and_return_persisted_entity() {
        queryGateway.single = new TestEntity(12, "已保存");

        TestEntity saved = repository.save(new TestEntity(12, "待保存"));

        Assertions.assertEquals(12, saved.id);
        Assertions.assertEquals(CommandOperation.UPDATE, commandGateway.lastSpec.getOp());
        Assertions.assertEquals(QueryOperation.DETAIL, queryGateway.lastSpec.getOp());
        Assertions.assertEquals(12, queryGateway.lastSpec.getFilters().get(0).getValue());
    }

    private EntityMetaRegistry metaRegistry() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Integer.class, "id", false, false, true, true));
        fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
        ResourceDescriptor descriptor = new ResourceDescriptor(
            TestEntity.class,
            "testEntity",
            "test-service",
            Collections.<String>emptyList()
        );
        EntityMeta meta = new EntityMeta(
            TestEntity.class,
            descriptor,
            "test_entity",
            "id",
            EntityIdPolicy.GENERATED,
            null,
            fields
        );
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return meta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return descriptor;
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return RelationGraph.empty();
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    private static final class TestEntity {
        /** 测试实体主键。 */
        private Integer id;
        /** 测试实体名称。 */
        private String name;

        private TestEntity(Integer id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class RecordingQueryGateway implements QueryGateway {
        private QuerySpec<?> lastSpec;
        private TestEntity single;
        private long total;

        @Override
        public <R> PageResult<R> page(QuerySpec<R> spec) {
            lastSpec = spec;
            PageResult<R> result = new PageResult<R>();
            result.setTotal(total);
            result.setPage(spec.getPage().getPage());
            result.setLimit(spec.getPage().getLimit());
            return result;
        }

        @Override
        public <R> List<R> list(QuerySpec<R> spec) {
            lastSpec = spec;
            return Collections.emptyList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R findOne(QuerySpec<R> spec) {
            lastSpec = spec;
            return (R) single;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R detail(QuerySpec<R> spec) {
            lastSpec = spec;
            return (R) single;
        }
    }

    private static final class RecordingCommandGateway implements CommandGateway {
        private CommandSpec<?> lastSpec;

        @Override
        public <P, R> R action(CommandSpec<P> spec) {
            lastSpec = spec;
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            if (spec.getOp() == CommandOperation.CREATE) {
                result.put("id", 11L);
                result.put("rows", 1);
            } else if (spec.getOp() == CommandOperation.SAVE_OR_UPDATE) {
                result.put("id", 12L);
                result.put("rows", 1);
            } else if (spec.getPayload() instanceof BatchCommand<?>) {
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                int index = 0;
                for (Object ignored : ((BatchCommand<?>) spec.getPayload()).getItems()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", Long.valueOf(101 + index));
                    item.put("rows", 1);
                    items.add(item);
                    index++;
                }
                result.put("items", items);
                result.put("rows", items.size());
            } else {
                result.put("rows", 2);
            }
            @SuppressWarnings("unchecked")
            R cast = (R) result;
            return cast;
        }
    }

    private static final class RecordingTransactionExecutor implements CrudWriteTransactionExecutor {
        private CrudWriteTransactionPolicy lastPolicy;

        @Override
        public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
            lastPolicy = policy;
            return callback.execute();
        }
    }
}
