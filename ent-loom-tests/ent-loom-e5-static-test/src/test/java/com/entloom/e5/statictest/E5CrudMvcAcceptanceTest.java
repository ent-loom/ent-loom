package com.entloom.e5.statictest;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.permission.AllowAllCrudPermissionService;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E5.2：复用 CustomerProfile 的 H2 + MockMvc CRUD 最小验收。 */
class E5CrudMvcAcceptanceTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(E5CrudMvcTestConfiguration.class, CrudAutoConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full"
        );

    @Test
    @DisplayName("CustomerProfile 应通过 MockMvc 完成 create、detail、update、delete")
    void shouldCompleteCustomerProfileCrudFlow() {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/customer_profile/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"e5-create-1\"},\"payload\":{\"id\":501,\"displayName\":\"张三\",\"creditLimit\":1000.00,\"registeredAt\":\"2026-08-25T10:30:00\",\"avatarUrl\":\"https://example.test/avatar.png\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("e5-create-1"))
                .andExpect(jsonPath("$.operation").value("CREATE"))
                .andExpect(jsonPath("$.data.id").value(501));

            mockMvc.perform(post("/api/ent-crud/customer_profile/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":501}},\"requestId\":\"e5-detail-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("e5-detail-1"))
                .andExpect(jsonPath("$.operation").value("DETAIL"))
                .andExpect(jsonPath("$.data.item.display_name").value("张三"));

            mockMvc.perform(post("/api/ent-crud/customer_profile/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"e5-update-1\"},\"payload\":{\"id\":501,\"displayName\":\"张三（更新）\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("e5-update-1"))
                .andExpect(jsonPath("$.operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows").value(1));

            mockMvc.perform(post("/api/ent-crud/customer_profile/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":501}},\"requestId\":\"e5-detail-2\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("e5-detail-2"))
                .andExpect(jsonPath("$.operation").value("DETAIL"))
                .andExpect(jsonPath("$.data.item.display_name").value("张三（更新）"));

            mockMvc.perform(post("/api/ent-crud/customer_profile/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"e5-delete-1\"},\"payload\":{\"id\":501}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("e5-delete-1"))
                .andExpect(jsonPath("$.operation").value("DELETE"))
                .andExpect(jsonPath("$.data.rows").value(1));
        });
    }

    private MockMvc buildMockMvc(AssertableApplicationContext context) {
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        return MockMvcBuilders.standaloneSetup(
                context.getBean(EntCrudQueryController.class),
                context.getBean(EntCrudCommandController.class)
            )
            .setControllerAdvice(context.getBean(CrudHttpExceptionTranslator.class))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    /** 为 E5.2 提供实际 JDBC CRUD 所需的最小单实体测试装配。 */
    @Configuration
    static class E5CrudMvcTestConfiguration {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                "jdbc:h2:mem:e5_customer_profile;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
            );
        }

        @Bean
        InitializingBean customerProfileSchemaInitializer(DataSource dataSource) {
            return () -> {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                jdbcTemplate.execute("DROP TABLE IF EXISTS customer_profile");
                jdbcTemplate.execute(
                    "CREATE TABLE customer_profile("
                        + "id BIGINT PRIMARY KEY,"
                        + "display_name VARCHAR(64) NOT NULL,"
                        + "credit_limit DECIMAL(10,2) NOT NULL,"
                        + "registered_at TIMESTAMP NOT NULL,"
                        + "avatar_url VARCHAR(255)"
                        + ")"
                );
            };
        }

        @Bean
        EntityMetaRegistry metaRegistry() {
            EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(
                    E5EntityRuntimeStaticAcceptanceTest.CustomerProfile.class))
            );
            registry.validateOrThrow();
            return registry;
        }

        @Bean
        CrudPermissionService crudPermissionService() {
            return new AllowAllCrudPermissionService();
        }

        @Bean
        CrudDataScopeResolver crudDataScopeResolver() {
            return new AllowAllCrudDataScopeResolver();
        }

        @Bean
        CrudSubjectResolver crudSubjectResolver() {
            return () -> {
                SubjectContext subject = new SubjectContext();
                subject.setSubjectId("e5-test-user");
                subject.setTenantId("e5-test-tenant");
                return subject;
            };
        }

        @Bean
        ExposedEntityRegistry exposedEntityRegistry(EntityMetaRegistry metaRegistry) {
            ExposedEntityRegistry registry = new ExposedEntityRegistry(metaRegistry);
            registry.expose(E5EntityRuntimeStaticAcceptanceTest.CustomerProfile.class);
            return registry;
        }
    }
}
