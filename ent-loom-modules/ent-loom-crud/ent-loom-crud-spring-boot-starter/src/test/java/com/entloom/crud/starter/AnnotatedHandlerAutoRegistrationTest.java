package com.entloom.crud.starter;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.enums.QueryStrategy;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.support.TestOrderEntity;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnotatedHandlerAutoRegistrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            CrudAutoConfiguration.class,
            StarterJdbcTestSupportConfiguration.class,
            AutoRegistrationTestConfiguration.class
        )
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full"
        );

    @Test
    void should_route_registered_action_and_custom_detail_handler() {
        contextRunner.run(context -> {
            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            QueryGateway queryGateway = context.getBean(QueryGateway.class);

            CommandResult<Object> actionResult = commandGateway.action(actionSpec("ORDER.PLACE"));
            assertThat(actionResult.isSuccess()).isTrue();
            assertThat(actionResult.getData()).isInstanceOf(PlaceOrderResponse.class);
            PlaceOrderResponse actionData = (PlaceOrderResponse) actionResult.getData();
            assertThat(actionData.getScene()).isEqualTo("order.place");
            assertThat(actionData.getHandledBy()).isEqualTo("place-order-action");

            Map<String, Object> detailResult = queryGateway.detail(detailSpec());
            assertThat(detailResult).containsEntry("source", "custom-query-handler");
            assertThat(detailResult).containsEntry("scene", "order.detail");
        });
    }

    @Test
    void should_expose_registered_action_over_http() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/action/order.place")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-action-http\"},\"payload\":{\"orderNo\":\"ORD-HTTP\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-action-http"))
                .andExpect(jsonPath("$.operation").value("ACTION"))
                .andExpect(jsonPath("$.data.scene").value("order.place"))
                .andExpect(jsonPath("$.data.handledBy").value("place-order-action"))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-HTTP"));
        });
    }

    @Test
    void should_inject_handler_default_strategy_from_annotation() {
        contextRunner.run(context -> {
            QueryGateway queryGateway = context.getBean(QueryGateway.class);
            QuerySpec<Map<String, Object>> spec = detailSpec("order.strategy", QueryStrategy.DEFAULT);

            Map<String, Object> detailResult = queryGateway.detail(spec);
            assertThat(detailResult).containsEntry("handlerDefaultStrategy", QueryStrategy.ROOT_FIRST.name());
            assertThat(detailResult).containsEntry("inputStrategy", QueryStrategy.DEFAULT.name());
            assertThat(spec.getHandlerDefaultStrategy()).isEqualTo(QueryStrategy.DEFAULT);
        });
    }

    private CommandSpec<Object> actionSpec(String scene) {
        PlaceOrderRequest payload = new PlaceOrderRequest();
        payload.setOrderNo("ORD-ACTION");
        return CommandSpec.<Object>builder()
            .scene(scene)
            .rootType(TestOrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestOrderEntity.class))
            .op(CommandOperation.ACTION)
            .subject(testSubject())
            .payload(payload)
            .resultType(commandResultType())
            .build();
    }

    private QuerySpec<Map<String, Object>> detailSpec() {
        return detailSpec("order.detail", QueryStrategy.DEFAULT);
    }

    private QuerySpec<Map<String, Object>> detailSpec(String scene, QueryStrategy strategy) {
        return QuerySpec.<Map<String, Object>>builder()
            .scene(scene)
            .rootType(TestOrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestOrderEntity.class))
            .op(QueryOperation.DETAIL)
            .strategy(strategy)
            .subject(testSubject())
            .resultType(mapType())
            .build();
    }

    private SubjectContext testSubject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("test-tenant");
        return subject;
    }

    @SuppressWarnings("unchecked")
    private Class<CommandResult<Object>> commandResultType() {
        return (Class<CommandResult<Object>>) (Class<?>) CommandResult.class;
    }

    @SuppressWarnings("unchecked")
    private Class<Map<String, Object>> mapType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    private MockMvc buildMockMvc(AssertableApplicationContext context) {
        return MockMvcBuilders.standaloneSetup(
                context.getBean(com.entloom.crud.starter.web.controller.EntCrudQueryController.class),
                context.getBean(com.entloom.crud.starter.web.controller.EntCrudCommandController.class),
                context.getBean(com.entloom.crud.starter.web.controller.EntCrudStatsController.class)
            )
            .setControllerAdvice(context.getBean(com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator.class))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Configuration
    static class AutoRegistrationTestConfiguration {
        @Bean
        public PlaceOrderAction placeOrderAction() {
            return new PlaceOrderAction();
        }

        @Bean
        public TestOrderDetailQueryHandler testOrderDetailQueryHandler() {
            return new TestOrderDetailQueryHandler();
        }

        @Bean
        public StrategyAwareDetailQueryHandler strategyAwareDetailQueryHandler() {
            return new StrategyAwareDetailQueryHandler();
        }
    }

    @EntCrudCommandAction(
        entityClass = TestOrderEntity.class,
        scene = "order.place",
        requestType = PlaceOrderRequest.class,
        responseType = PlaceOrderResponse.class
    )
    static class PlaceOrderAction implements CommandActionSceneHandler<PlaceOrderRequest, PlaceOrderResponse> {
        private static final Set<CrudRouteKey> ROUTE_KEYS = Collections.singleton(
            new CrudRouteKey(
                Collections.singletonList(TestOrderEntity.class.getName()),
                CrudOperationKey.of(CommandOperation.ACTION),
                RouteKeyFactory.normalizeScene("order.place")
            )
        );

        @Override
        public Set<CrudRouteKey> routeKeys() {
            return ROUTE_KEYS;
        }

        @Override
        public CommandActionContract contract() {
            return new CommandActionContract(PlaceOrderRequest.class, PlaceOrderResponse.class);
        }

        @Override
        public CommandResult<PlaceOrderResponse> handle(
            CommandSpec<PlaceOrderRequest> spec,
            SceneDelegate<CommandSpec<PlaceOrderRequest>, CommandResult<PlaceOrderResponse>> delegate
        ) {
            PlaceOrderResponse data = new PlaceOrderResponse();
            data.setScene(RouteKeyFactory.normalizeScene(spec.getScene()));
            data.setHandledBy("place-order-action");
            data.setOrderNo(spec.getPayload() == null ? null : spec.getPayload().getOrderNo());
            return CommandResult.success(data);
        }
    }

    @EntCrudQueryHandler(entityClasses = {TestOrderEntity.class}, scenes = {"order.detail"})
    static class TestOrderDetailQueryHandler implements QueryDetailSceneHandler<Map<String, Object>> {
        private static final Set<CrudRouteKey> ROUTE_KEYS = Collections.singleton(
            new CrudRouteKey(
                Collections.singletonList(TestOrderEntity.class.getName()),
                CrudOperationKey.of(QueryOperation.DETAIL),
                RouteKeyFactory.normalizeScene("order.detail")
            )
        );

        @Override
        public Set<CrudRouteKey> routeKeys() {
            return ROUTE_KEYS;
        }

        @Override
        public Map<String, Object> handle(
            QuerySpec<Map<String, Object>> spec,
            SceneDelegate<QuerySpec<Map<String, Object>>, Map<String, Object>> delegate
        ) {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
            result.put("source", "custom-query-handler");
            result.put("scene", spec.getScene());
            return result;
        }
    }

    @EntCrudQueryHandler(
        entityClasses = {TestOrderEntity.class},
        scenes = {"order.strategy"},
        defaultStrategy = QueryStrategy.ROOT_FIRST
    )
    static class StrategyAwareDetailQueryHandler implements QueryDetailSceneHandler<Map<String, Object>> {
        private static final Set<CrudRouteKey> ROUTE_KEYS = Collections.singleton(
            new CrudRouteKey(
                Collections.singletonList(TestOrderEntity.class.getName()),
                CrudOperationKey.of(QueryOperation.DETAIL),
                RouteKeyFactory.normalizeScene("order.strategy")
            )
        );

        @Override
        public Set<CrudRouteKey> routeKeys() {
            return ROUTE_KEYS;
        }

        @Override
        public QueryStrategy defaultStrategy() {
            return QueryStrategy.ROOT_FIRST;
        }

        @Override
        public Map<String, Object> handle(
            QuerySpec<Map<String, Object>> spec,
            SceneDelegate<QuerySpec<Map<String, Object>>, Map<String, Object>> delegate
        ) {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
            result.put("handlerDefaultStrategy", spec.getHandlerDefaultStrategy().name());
            result.put("inputStrategy", spec.getStrategy().name());
            return result;
        }
    }

    @Getter
    @Setter
    static class PlaceOrderRequest {
        private String orderNo;
    }

    @Getter
    @Setter
    static class PlaceOrderResponse {
        private String scene;
        private String handledBy;
        private String orderNo;
    }
}
