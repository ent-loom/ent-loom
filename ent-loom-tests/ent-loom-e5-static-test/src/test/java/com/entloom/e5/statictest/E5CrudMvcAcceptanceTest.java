package com.entloom.e5.statictest;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.adapter.PortalResolver;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.policy.ScenePolicy;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.util.RouteKeyFactory;
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
import com.entloom.e5.statictest.fixture.CustomerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

            assertSuccessfulOperation(mockMvc, "create", createRequest(), "e5-create-1", "CREATE",
                jsonPath("$.data.id").value(501));
            assertSuccessfulOperation(mockMvc, "detail", detailRequest("e5-detail-1"), "e5-detail-1", "DETAIL",
                jsonPath("$.data.item.display_name").value("张三"));
            assertSuccessfulOperation(mockMvc, "update", updateRequest(), "e5-update-1", "UPDATE",
                jsonPath("$.data.rows").value(1));
            assertSuccessfulOperation(mockMvc, "detail", detailRequest("e5-detail-2"), "e5-detail-2", "DETAIL",
                jsonPath("$.data.item.display_name").value("张三（更新）"));
            assertSuccessfulOperation(mockMvc, "delete", deleteRequest(), "e5-delete-1", "DELETE",
                jsonPath("$.data.rows").value(1));
        });
    }

    @Test
    @DisplayName("CustomerProfile ACTION 应允许 HTTP，拒绝 SDK 和未配置场景")
    void shouldEnforceCustomerProfileActionPolicy() {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);
            mockMvc.perform(post("/api/ent-crud/customer_profile/action/profile.activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"e5-action-http\"},\"payload\":{\"profileId\":501}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profileId").value(501))
                .andExpect(jsonPath("$.data.status").value("activated"));

            CommandGateway gateway = context.getBean(CommandGateway.class);
            assertThrows(PermissionDeniedException.class, () -> gateway.action(actionSpec("profile.activate")));

            assertThrows(PermissionDeniedException.class, () -> CrudRequestContextHolder.withAttribute(
                PortalResolver.ATTRIBUTE_KEY,
                "http",
                () -> gateway.action(actionSpec("profile.unconfigured"))
            ));
        });
    }

    private static CommandSpec<ProfileActionRequest> actionSpec(String scene) {
        ProfileActionRequest payload = new ProfileActionRequest();
        payload.setProfileId(501L);
        return CommandSpec.<ProfileActionRequest>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(CommandOperation.ACTION)
            .scene(scene)
            .payload(payload)
            .resultType(commandResultType())
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Class<CommandResult<ProfileActionResponse>> commandResultType() {
        return (Class<CommandResult<ProfileActionResponse>>) (Class<?>) CommandResult.class;
    }

    private static void assertSuccessfulOperation(MockMvc mockMvc,
                                                  String resourceOperation,
                                                  String requestBody,
                                                  String requestId,
                                                  String operation,
                                                  ResultMatcher resultMatcher) throws Exception {
        mockMvc.perform(post("/api/ent-crud/customer_profile/" + resourceOperation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.requestId").value(requestId))
            .andExpect(jsonPath("$.operation").value(operation))
            .andExpect(resultMatcher);
    }

    private static String createRequest() {
        return "{\"options\":{\"requestId\":\"e5-create-1\"},\"payload\":{\"id\":501,\"displayName\":\"张三\","
            + "\"creditLimit\":1000.00,\"registeredAt\":\"2026-08-25T10:30:00\","
            + "\"avatarUrl\":\"https://example.test/avatar.png\"}}";
    }

    private static String detailRequest(String requestId) {
        return "{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":501}},\"requestId\":\""
            + requestId + "\"}}";
    }

    private static String updateRequest() {
        return "{\"options\":{\"requestId\":\"e5-update-1\"},\"payload\":{\"id\":501,\"displayName\":\"张三（更新）\"}}";
    }

    private static String deleteRequest() {
        return "{\"options\":{\"requestId\":\"e5-delete-1\"},\"payload\":{\"id\":501}}";
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
                    CustomerProfile.class))
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
            registry.expose(CustomerProfile.class);
            return registry;
        }

        @Bean
        ProfileActivateAction profileActivateAction() {
            return new ProfileActivateAction();
        }

        @Bean
        ScenePolicy profileActivatePolicy() {
            return new ScenePolicy(
                new com.entloom.crud.core.governance.policy.ScenePolicyKey(
                    "base",
                    "customer_profile",
                    CrudOperationKey.of(CommandOperation.ACTION),
                    "profile.activate"
                ),
                "profile:activate",
                Collections.singleton("http")
            );
        }
    }

    /** 客户档案激活请求。 */
    public static final class ProfileActionRequest {
        /** 客户档案主键。 */
        private Long profileId;

        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
    }

    /** 客户档案激活结果。 */
    public static final class ProfileActionResponse {
        /** 客户档案主键。 */
        private Long profileId;
        /** 激活后状态。 */
        private String status;

        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @EntCrudCommandAction(
        entityClass = CustomerProfile.class,
        scene = "profile.activate",
        requestType = ProfileActionRequest.class,
        responseType = ProfileActionResponse.class
    )
    static final class ProfileActivateAction implements CommandActionSceneHandler<ProfileActionRequest, ProfileActionResponse> {
        private static final Set<CrudRouteKey> ROUTE_KEYS = Collections.singleton(
            new CrudRouteKey(
                Collections.singletonList(CustomerProfile.class.getName()),
                CrudOperationKey.of(CommandOperation.ACTION),
                RouteKeyFactory.normalizeScene("profile.activate")
            )
        );

        @Override
        public Set<CrudRouteKey> routeKeys() { return ROUTE_KEYS; }

        @Override
        public CommandActionContract contract() {
            return new CommandActionContract(ProfileActionRequest.class, ProfileActionResponse.class);
        }

        @Override
        public CommandResult<ProfileActionResponse> handle(
            CommandSpec<ProfileActionRequest> spec,
            SceneDelegate<CommandSpec<ProfileActionRequest>, CommandResult<ProfileActionResponse>> delegate
        ) {
            ProfileActionResponse response = new ProfileActionResponse();
            response.setProfileId(spec.getPayload().getProfileId());
            response.setStatus("activated");
            return CommandResult.success(response);
        }
    }
}
