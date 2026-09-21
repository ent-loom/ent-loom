package com.entloom.crud.starter.dao;

import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.support.TestOrderEntity;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Starter 代理把对象参数、投影和自定义写命令接到真实 JDBC 执行链。 */
class EntDaoCustomMethodIntegrationTest {
    @Test
    void spring_proxy_should_bind_object_parameter_and_record_projection() {
        runner().run(context -> {
            OrderDao dao = context.getBean(OrderDao.class);

            OrderSummary summary = dao.findSummary(new OrderFilter(2L));

            assertThat(summary).isEqualTo(new OrderSummary(2L, "ORD-2", Boolean.TRUE));

            PageResult<TestOrderEntity> page = dao.findPage(
                new OrderFilter(null, "ORD-2"),
                new PageQuery(1, 1)
            );
            assertThat(page.getItems()).extracting(TestOrderEntity::getId).containsExactly(2L);
        });
    }

    @Test
    void custom_command_should_participate_in_outer_transaction_and_rollback() {
        runner().run(context -> {
            OrderDao dao = context.getBean(OrderDao.class);
            TransactionTemplate transaction = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class)
            );

            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                assertThat(dao.rename(new OrderCommand(2L, "ROLLBACK-ME"))).isEqualTo(1);
                throw new IllegalStateException("测试回滚");
            })).isInstanceOf(IllegalStateException.class).hasMessage("测试回滚");

            assertThat(dao.findById(2L)).get().extracting(TestOrderEntity::getOrderNo).isEqualTo("ORD-2");
        });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withUserConfiguration(
                StarterJdbcTestSupportConfiguration.class,
                TransactionalTestConfiguration.class,
                CrudAutoConfiguration.class
            )
            .withBean(EntityDaoScopeResolver.class, () -> entityType -> EntityAccessScope.unrestricted())
            .withBean("orderDao", EntDaoFactoryBean.class, () -> new EntDaoFactoryBean<>(OrderDao.class));
    }

    @EntDao
    interface OrderDao extends com.entloom.crud.core.capability.dao.EntityDao<TestOrderEntity, Long> {
        @EntQuery("select id, order_no, paid from test_order where id = :filter.id")
        OrderSummary findSummary(OrderFilter filter);

        @EntQuery("select * from test_order where order_no = :filter.orderNo")
        PageResult<TestOrderEntity> findPage(OrderFilter filter, PageQuery pageQuery);

        @EntCommand("update test_order set order_no = :command.orderNo where id = :command.id")
        int rename(OrderCommand command);
    }

    public static final class OrderFilter {
        private final Long id;
        private final String orderNo;

        public OrderFilter(Long id) {
            this(id, null);
        }

        public OrderFilter(Long id, String orderNo) {
            this.id = id;
            this.orderNo = orderNo;
        }

        public Long getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }
    }

    public static final class OrderCommand {
        private final Long id;
        private final String orderNo;

        public OrderCommand(Long id, String orderNo) {
            this.id = id;
            this.orderNo = orderNo;
        }

        public Long getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }
    }

    public record OrderSummary(Long id, String orderNo, Boolean paid) {
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionalTestConfiguration {
        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
