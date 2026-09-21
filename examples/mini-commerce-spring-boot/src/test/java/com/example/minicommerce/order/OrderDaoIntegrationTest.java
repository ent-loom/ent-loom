package com.example.minicommerce.order;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.exception.EntityDaoConstraintException;
import com.entloom.crud.core.governance.permission.AllowAllCrudPermissionService;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.starter.dao.EntDaoScan;
import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.product.dao.ProductDao;
import com.example.minicommerce.product.entity.Product;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.exception.OrderValidationException;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.service.OrderQueryService;
import com.example.minicommerce.order.service.PlaceOrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实扫描代理、JDBC 和服务事务验证订单闭环；测试自身不包裹事务。 */
class OrderDaoIntegrationTest {
    @Test
    void shouldPersistGeneratedKeysAndAssembleDetail() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            seed(context);
            var result = context.getBean(PlaceOrderService.class).handle(command());
            var detail = context.getBean(OrderQueryService.class).findDetail(result.orderId());
            assertEquals("测试客户", detail.customerName());
            assertEquals(new BigDecimal("30.00"), detail.totalAmount());
            assertEquals(List.of(10L, 20L), detail.items().stream().map(OrderDetail.Item::productId).toList());
            var items = context.getBean(OrderItemDao.class).findByOrderId(result.orderId());
            assertTrue(items.stream().allMatch(item -> item.getId() != null && result.orderId().equals(item.getOrderId())));
            assertThrows(OrderValidationException.class,
                () -> context.getBean(OrderQueryService.class).findDetail(-1L));
            Order order = new Order();
            order.setCustomerId(1L);
            order.setStatus(OrderStatus.CREATED);
            order.setTotalAmount(BigDecimal.ONE);
            order.setCreatedAt(LocalDateTime.now());
            assertEquals(context.getBean(OrderDao.class).insert(order), order.getId());
        }
    }

    @Test
    void secondItemFailureShouldRollbackOrderAndFirstItem() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            seed(context);
            var jdbc = context.getBean(JdbcTemplate.class);
            // 第二条明细违反真实数据库约束，第一条明细已执行 INSERT。
            jdbc.execute("alter table commerce_order_item add constraint reject_second check(product_id <> 20)");
            var failure = assertThrows(EntityDaoConstraintException.class,
                () -> context.getBean(PlaceOrderService.class).handle(command()));
            assertTrue(failure.getCause().getMessage().toLowerCase(Locale.ROOT)
                .contains("reject_second"), "必须由第二条明细约束触发失败");
            assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order_item", Integer.class));
        }
    }

    private void seed(AnnotationConfigApplicationContext context) {
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("insert into customer values (1, '测试客户', 'test@example.com')");
        jdbc.update("insert into product values (10, '商品一', 10, true), (20, '商品二', 20, true)");
    }

    private PlaceOrderCommand command() {
        return new PlaceOrderCommand(1L, List.of(
            new PlaceOrderItem(10L, 1),
            new PlaceOrderItem(20L, 1)
        ));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EntDaoScan(basePackageClasses = {CustomerDao.class, ProductDao.class, OrderDao.class})
    @Import({PlaceOrderService.class, OrderQueryService.class})
    static class Config {
        @Bean DataSource dataSource() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
            return source;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean EntityMetaRegistry entityMetaRegistry() {
            return new CrudRuntimeModelBackedEntityMetaRegistry(new CrudNativeRuntimeModelParser()
                .parse(List.of(Customer.class, Product.class, Order.class, OrderItem.class)));
        }
        @Bean EntityDaoFactory entityDaoFactory(EntityMetaRegistry registry, JdbcTemplate jdbc) {
            var guard = new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(registry), new SqlParameterLimiter());
            return new JdbcEntityDaoFactory(registry,
                new JdbcGuardedSqlExecutor(jdbc, guard, new SqlExecutionLogger()), StandardJdbcDialect.H2);
        }
        @Bean EntityDaoScopeResolver entityDaoScopeResolver() {
            return type -> EntityAccessScope.unrestricted();
        }
        @Bean OrderAccessPolicy orderAccessPolicy() {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId("订单集成测试");
            return new OrderAccessPolicy(() -> subject, new AllowAllCrudPermissionService());
        }
    }
}
