package com.entloom.crud.engine.jdbc;

import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.repository.DefaultEntityRepositoryFactory;
import com.entloom.crud.core.repository.EntityPatch;
import com.entloom.crud.core.repository.EntityQuery;
import com.entloom.crud.core.repository.EntityRepository;
import com.entloom.crud.engine.jdbc.test.entity.RepositoryOrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于真实 H2 JDBC、Gateway 和事务管理器的 Repository 集成测试。
 */
class EntityRepositoryJdbcIntegrationTest extends EngineJdbcTestSupport {
    @Test
    void insert_should_return_database_generated_id_and_save_should_reload_entity() {
        EntityRepository<RepositoryOrderTestEntity, Long> repository = repository();

        Long id = repository.insert(new RepositoryOrderTestEntity("ORD-INSERT"));
        Assertions.assertNotNull(id);
        Assertions.assertEquals("ORD-INSERT", repository.getRequired(id).getOrderNo());

        RepositoryOrderTestEntity saved = repository.save(new RepositoryOrderTestEntity("ORD-SAVE"));
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals("ORD-SAVE", saved.getOrderNo());

        saved.setOrderNo("ORD-SAVE-UPDATED");
        RepositoryOrderTestEntity updated = repository.save(saved);
        Assertions.assertEquals(saved.getId(), updated.getId());
        Assertions.assertEquals("ORD-SAVE-UPDATED", updated.getOrderNo());
    }

    @Test
    void save_batch_should_preserve_order_and_return_generated_ids() {
        EntityRepository<RepositoryOrderTestEntity, Long> repository = repository();
        RepositoryOrderTestEntity existing = repository.save(new RepositoryOrderTestEntity("ORD-EXISTING"));
        existing.setOrderNo("ORD-EXISTING-UPDATED");

        List<RepositoryOrderTestEntity> saved = repository.saveBatch(Arrays.asList(
            existing,
            new RepositoryOrderTestEntity("ORD-BATCH-CREATED")
        ));

        Assertions.assertEquals(2, saved.size());
        Assertions.assertEquals(existing.getId(), saved.get(0).getId());
        Assertions.assertEquals("ORD-EXISTING-UPDATED", saved.get(0).getOrderNo());
        Assertions.assertNotNull(saved.get(1).getId());
        Assertions.assertEquals("ORD-BATCH-CREATED", saved.get(1).getOrderNo());
    }

    @Test
    void logical_delete_and_conditional_writes_should_return_affected_rows() {
        EntityRepository<RepositoryOrderTestEntity, Long> repository = repository();
        repository.insert(new RepositoryOrderTestEntity("ORD-CONDITION-A"));
        repository.insert(new RepositoryOrderTestEntity("ORD-CONDITION-B"));

        int updated = repository.update(
            EntityQuery.<RepositoryOrderTestEntity>builder().in("orderNo", Arrays.asList("ORD-CONDITION-A", "ORD-CONDITION-B")).build(),
            EntityPatch.<RepositoryOrderTestEntity>builder().set("priority", 1).build()
        );
        Assertions.assertEquals(2, updated);

        int deleted = repository.delete(
            EntityQuery.<RepositoryOrderTestEntity>builder().eq("priority", 1).build()
        );
        Assertions.assertEquals(2, deleted);
        Assertions.assertEquals(0L, repository.count(EntityQuery.<RepositoryOrderTestEntity>empty()));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject(
            "select count(1) from t_repository_order where is_deleted = 1", Long.class).longValue());
    }

    @Test
    void save_batch_should_roll_back_all_rows_when_one_insert_fails() {
        EntityRepository<RepositoryOrderTestEntity, Long> repository = repository();

        Assertions.assertThrows(
            RuntimeException.class,
            () -> repository.saveBatch(Arrays.asList(
                new RepositoryOrderTestEntity("ORD-ROLLBACK"),
                new RepositoryOrderTestEntity("ORD-ROLLBACK")
            ))
        );

        Assertions.assertEquals(0L, jdbcTemplate.queryForObject(
            "select count(1) from t_repository_order", Long.class).longValue());
    }

    private EntityRepository<RepositoryOrderTestEntity, Long> repository() {
        return new DefaultEntityRepositoryFactory(
            queryGateway,
            commandGateway,
            metaRegistry,
            transactionExecutor(dataSource)
        ).repository(RepositoryOrderTestEntity.class, Long.class);
    }

    private CrudWriteTransactionExecutor transactionExecutor(DataSource source) {
        TransactionTemplate template = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new CrudWriteTransactionExecutor() {
            @Override
            public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
                if (policy == null || policy == CrudWriteTransactionPolicy.NONE) {
                    return callback.execute();
                }
                return template.execute(status -> callback.execute());
            }
        };
    }
}
