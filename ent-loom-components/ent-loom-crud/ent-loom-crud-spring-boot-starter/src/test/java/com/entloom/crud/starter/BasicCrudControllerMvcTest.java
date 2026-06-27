package com.entloom.crud.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BasicCrudControllerMvcTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CrudAutoConfiguration.class, StarterJdbcTestSupportConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full"
        );
    private final ApplicationContextRunner defaultLikeContextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CrudAutoConfiguration.class, StarterJdbcTestSupportConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full",
            "entloom.crud.controller.string-filter.default-like-enabled=true"
        );
    private final ApplicationContextRunner defaultLikeExcludeOrderNoContextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CrudAutoConfiguration.class, StarterJdbcTestSupportConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full",
            "entloom.crud.controller.string-filter.default-like-enabled=true",
            "entloom.crud.controller.string-filter.default-like-exclude-fields=orderNo"
        );

    @Test
    void controller_should_page_test_entity() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-page-1"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.page.total").value(21))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.meta.schema.kind").value("record"))
                .andExpect(jsonPath("$.meta.schema.entity").value("TestOrderEntity"))
                .andExpect(jsonPath("$.meta.schema.viewType").value("com.entloom.crud.api.model.CrudRecord"))
                .andExpect(jsonPath("$.meta.schema.fields[0].name").value("id"));
        });
    }

    @Test
    void controller_should_support_registered_view_type() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":2,\"requestId\":\"req-page-view-1\",\"viewType\":\"orderSummary\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-view-1"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("ORD-1"))
                .andExpect(jsonPath("$.meta.schema.kind").value("view"))
                .andExpect(jsonPath("$.meta.schema.viewType").value("com.entloom.crud.starter.support.TestOrderSummaryView"))
                .andExpect(jsonPath("$.meta.schema.fields[0].name").value("id"));
        });
    }

    @Test
    void controller_should_list_test_entity() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"limit\":3,\"requestId\":\"req-list-1\",\"filterMap\":{\"id\":{\"op\":\"GE\",\"value\":5}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-list-1"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(5))
                .andExpect(jsonPath("$.data.items[1].id").value(6))
                .andExpect(jsonPath("$.data.items[2].id").value(7))
                .andExpect(jsonPath("$.meta.schema.kind").value("record"))
                .andExpect(jsonPath("$.meta.schema.entity").value("TestOrderEntity"));
        });
    }

    @Test
    void controller_should_reject_invalid_result_mode() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":2,\"requestId\":\"req-page-result-mode-invalid\",\"resultMode\":\"DTO\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-page-result-mode-invalid"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.stage").value("HTTP_CONTRACT"))
                .andExpect(jsonPath("$.error.reason").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.requestId").value("req-page-result-mode-invalid"));
        });
    }

    @Test
    void controller_error_envelope_should_include_route_context() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page/missingScene")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":2,\"requestId\":\"req-page-route-miss\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value("req-page-route-miss"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.stage").value("ROUTE"))
                .andExpect(jsonPath("$.error.reason").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.routeKey").value("com.entloom.crud.starter.support.TestOrderEntity|QUERY/PAGE|missingscene"))
                .andExpect(jsonPath("$.error.requestId").value("req-page-route-miss"));
        });
    }

    @Test
    void controller_should_reject_invalid_count_mode() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":2,\"requestId\":\"req-page-count-mode-invalid\",\"countMode\":\"FAST\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-page-count-mode-invalid"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_removed_sort_expression_on_read_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-sort-expression-removed\",\"sortExpression\":\"id,DESC\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-page-sort-expression-removed"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_execute_single_table_crud_flow() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-create-1\"},\"payload\":{\"id\":101,\"orderNo\":\"ORD-101\",\"deleted\":0}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-create-1"))
                .andExpect(jsonPath("$.operation").value("CREATE"))
                .andExpect(jsonPath("$.data.id").value(101));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":101}},\"requestId\":\"req-detail-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-detail-1"))
                .andExpect(jsonPath("$.operation").value("DETAIL"))
                .andExpect(jsonPath("$.data.item.order_no").value("ORD-101"));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-update-1\"},\"payload\":{\"id\":101,\"orderNo\":\"ORD-101-UPDATED\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-update-1"))
                .andExpect(jsonPath("$.operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows").value(1));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-delete-1\"},\"payload\":{\"id\":101}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-delete-1"))
                .andExpect(jsonPath("$.operation").value("DELETE"))
                .andExpect(jsonPath("$.data.rows").value(1));
        });
    }

    @Test
    void controller_should_reject_target_filters_on_default_command_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/update")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-update-target-filters\",\"targetFilters\":[{\"field\":\"id\",\"op\":\"EQ\",\"value\":1}]},\"payload\":{\"orderNo\":\"ORD-TARGET\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-update-target-filters"))
                .andExpect(jsonPath("$.operation").value("UPDATE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.stage").value("HTTP_CONTRACT"))
                .andExpect(jsonPath("$.error.reason").value("OPTIONS_TARGET_FILTERS_FORBIDDEN"));
        });
    }

    @Test
    void controller_should_support_save_or_update_single_by_payload_id() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/saveOrUpdate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-save-or-update-update-1\"},\"payload\":{\"id\":1,\"orderNo\":\"ORD-1-SAVE-OR-UPDATE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-save-or-update-update-1"))
                .andExpect(jsonPath("$.data.operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows").value(1))
                .andExpect(jsonPath("$.data.id").value(1));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/saveOrUpdate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-save-or-update-create-1\"},\"payload\":{\"id\":302,\"orderNo\":\"ORD-302\",\"deleted\":0}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-save-or-update-create-1"))
                .andExpect(jsonPath("$.data.operation").value("CREATE"))
                .andExpect(jsonPath("$.data.rows").value(1))
                .andExpect(jsonPath("$.data.id").value(302));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":302}},\"requestId\":\"req-detail-302\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.order_no").value("ORD-302"));
        });
    }

    @Test
    void controller_should_support_create_batch_by_payload_items() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/createBatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-create-batch-1\"},\"payload\":{\"items\":[{\"id\":401,\"orderNo\":\"ORD-401\",\"deleted\":0},{\"id\":402,\"orderNo\":\"ORD-402\",\"deleted\":0}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-create-batch-1"))
                .andExpect(jsonPath("$.data.rows").value(2))
                .andExpect(jsonPath("$.data.items[0].operation").value("CREATE"))
                .andExpect(jsonPath("$.data.items[0].id").value(401))
                .andExpect(jsonPath("$.data.items[1].operation").value("CREATE"))
                .andExpect(jsonPath("$.data.items[1].id").value(402));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"limit\":5,\"requestId\":\"req-list-created-batch\",\"filterMap\":{\"id\":{\"op\":\"GE\",\"value\":401}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].order_no").value("ORD-401"))
                .andExpect(jsonPath("$.data.items[1].order_no").value("ORD-402"));
        });
    }

    @Test
    void controller_should_support_update_batch_by_payload_items_id() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/updateBatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-update-batch-1\"},\"payload\":{\"items\":[{\"id\":3,\"orderNo\":\"ORD-3-BATCH\"},{\"id\":4,\"orderNo\":\"ORD-4-BATCH\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-update-batch-1"))
                .andExpect(jsonPath("$.data.rows").value(2))
                .andExpect(jsonPath("$.data.items[0].operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.items[0].id").value(3))
                .andExpect(jsonPath("$.data.items[1].operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.items[1].id").value(4));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"limit\":5,\"requestId\":\"req-list-updated-batch\",\"filterMap\":{\"id\":{\"op\":\"IN\",\"value\":[3,4]}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].order_no").value("ORD-3-BATCH"))
                .andExpect(jsonPath("$.data.items[1].order_no").value("ORD-4-BATCH"));
        });
    }

    @Test
    void controller_should_support_save_or_update_batch_by_payload_items_id() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/saveOrUpdateBatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-save-or-update-batch-1\"},\"payload\":{\"items\":[{\"id\":1,\"orderNo\":\"ORD-1-BATCH\"},{\"id\":301,\"orderNo\":\"ORD-301\",\"deleted\":0}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-save-or-update-batch-1"))
                .andExpect(jsonPath("$.operation").value("SAVE_OR_UPDATE_BATCH"))
                .andExpect(jsonPath("$.data.rows").value(2))
                .andExpect(jsonPath("$.data.items[0].operation").value("UPDATE"))
                .andExpect(jsonPath("$.data.items[1].operation").value("CREATE"));

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":301}},\"requestId\":\"req-detail-301\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.order_no").value("ORD-301"));
        });
    }

    @Test
    void controller_should_support_delete_batch_by_payload_ids() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/deleteBatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-delete-batch-1\"},\"payload\":{\"ids\":[1,2]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-delete-batch-1"))
                .andExpect(jsonPath("$.operation").value("DELETE_BATCH"))
                .andExpect(jsonPath("$.data.rows").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[1].id").value(2));
        });
    }

    @Test
    void controller_should_support_find_one_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/findOne")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-find-one-1\",\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":1}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-find-one-1"))
                .andExpect(jsonPath("$.operation").value("FIND_ONE"))
                .andExpect(jsonPath("$.data.item.id").value(1));
        });
    }

    @Test
    void controller_should_return_null_when_find_one_miss() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/findOne")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-find-one-miss\",\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":999999}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-find-one-miss"))
                .andExpect(jsonPath("$.operation").value("FIND_ONE"))
                .andExpect(jsonPath("$.data.item").isEmpty());
        });
    }

    @Test
    void controller_should_fail_when_find_one_hits_multi_rows() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/findOne")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-find-one-multi\",\"filterMap\":{\"paid\":{\"op\":\"EQ\",\"value\":true}}}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requestId").value("req-find-one-multi"))
                .andExpect(jsonPath("$.operation").value("FIND_ONE"))
                .andExpect(jsonPath("$.code").value("QUERY_NOT_UNIQUE"));
        });
    }

    @Test
    void controller_should_reject_page_and_limit_on_find_one_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/findOne")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-find-one-limit\",\"page\":1,\"limit\":1,\"filterMap\":{\"id\":{\"op\":\"EQ\",\"value\":1}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-find-one-limit"))
                .andExpect(jsonPath("$.operation").value("FIND_ONE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_fail_when_detail_hits_multi_rows() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/detail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-detail-multi\",\"filterMap\":{\"paid\":{\"op\":\"EQ\",\"value\":true}}}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requestId").value("req-detail-multi"))
                .andExpect(jsonPath("$.operation").value("DETAIL"))
                .andExpect(jsonPath("$.code").value("QUERY_NOT_UNIQUE"));
        });
    }

    @Test
    void controller_should_execute_scalar_stats_count() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"filterMap\":{\"paid\":{\"op\":\"EQ\",\"value\":true}},\"requestId\":\"req-agg-1\"},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"paidOrders\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-agg-1"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.data.mode").value("SCALAR"))
                .andExpect(jsonPath("$.data.metrics.paidOrders").value(10))
                .andExpect(jsonPath("$.meta.schema.entity").value("TestOrderEntity"))
                .andExpect(jsonPath("$.meta.schema.mode").value("SCALAR"))
                .andExpect(jsonPath("$.meta.schema.metrics[0]").value("paidOrders"));
        });
    }

    @Test
    void controller_should_execute_grouped_stats_page() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"filterMap\":{\"paid\":{\"op\":\"EQ\",\"value\":true}},\"sorts\":[{\"field\":\"paidOrders\",\"direction\":\"DESC\"}],\"requestId\":\"req-agg-page-1\"},\"stats\":{\"groupBy\":[{\"field\":\"paymentChannel\"}],\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"paidOrders\"}],\"includeSummary\":true,\"includeTotalGroups\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-agg-page-1"))
                .andExpect(jsonPath("$.data.mode").value("PAGE"))
                .andExpect(jsonPath("$.data.columns.dimensions[0]").value("paymentChannel"))
                .andExpect(jsonPath("$.data.columns.metrics[0]").value("paidOrders"))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.summary.paidOrders").value(10))
                .andExpect(jsonPath("$.data.page.page").value(1))
                .andExpect(jsonPath("$.data.page.totalGroups").value(2))
                .andExpect(jsonPath("$.meta.schema.mode").value("PAGE"))
                .andExpect(jsonPath("$.meta.schema.dimensions[0]").value("paymentChannel"));
        });
    }

    @Test
    void controller_should_support_metric_sort_target_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"sorts\":[{\"field\":\"totalAmountSum\",\"direction\":\"DESC\",\"target\":\"METRIC\"}],\"requestId\":\"req-agg-page-target-1\"},\"stats\":{\"groupBy\":[{\"field\":\"paymentChannel\"}],\"metrics\":[{\"agg\":\"SUM\",\"field\":\"totalAmount\",\"alias\":\"totalAmountSum\"}],\"includeSummary\":true,\"includeTotalGroups\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-agg-page-target-1"))
                .andExpect(jsonPath("$.data.mode").value("PAGE"))
                .andExpect(jsonPath("$.data.rows[0].dimensions.paymentChannel").value("WECHAT"))
                .andExpect(jsonPath("$.data.rows[0].metrics.totalAmountSum").value("1150"));
        });
    }

    @Test
    void controller_should_reject_removed_sort_expression_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-sort-expression-removed\",\"sortExpression\":\"paidOrders,DESC\"},\"stats\":{\"groupBy\":[{\"field\":\"paymentChannel\"}],\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"paidOrders\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-stats-sort-expression-removed"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_stats_payload_sorts_field() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-payload-sorts-reject\"},\"stats\":{\"groupBy\":[{\"field\":\"paymentChannel\"}],\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"paidOrders\"}],\"sorts\":[{\"field\":\"paidOrders\",\"direction\":\"DESC\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value("req-stats-payload-sorts-reject"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_stats_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-error\"},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-page-error"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_empty_stats_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-stats-empty\"},\"stats\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-page-stats-empty"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_support_options_filters_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-options-filters\",\"filters\":[{\"field\":\"paid\",\"op\":\"EQ\",\"value\":true}]},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-stats-options-filters"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.data.mode").value("SCALAR"))
                .andExpect(jsonPath("$.data.metrics.orderCount").value(10));
        });
    }

    @Test
    void controller_should_support_filter_filter_map_filter_list_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-filter-layered\",\"filter\":{\"paid\":true},\"filterMap\":{\"id\":{\"op\":\"GE\",\"value\":10}},\"filterList\":[{\"field\":\"id\",\"op\":\"LE\",\"value\":16}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-filter-layered"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.page.total").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(10));
        });
    }

    @Test
    void controller_should_ignore_blank_string_in_filter_and_filter_map_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-ignore-blank\",\"filter\":{\"orderNo\":\"  \"},\"filterMap\":{\"status\":{\"op\":\"EQ\",\"value\":\"\"},\"id\":{\"op\":\"GE\",\"value\":10}}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-ignore-blank"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.page.total").value(12))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].id").value(10));
        });
    }

    @Test
    void controller_should_reject_structured_filter_value_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-structured-filter-reject\",\"filter\":{\"orderNo\":{\"op\":\"LIKE\",\"value\":\"%ORD%\"}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-page-structured-filter-reject"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_object_filter_value_without_op_on_page_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":10,\"requestId\":\"req-page-object-filter-reject\",\"filter\":{\"orderNo\":{\"keyword\":\"ORD\"}}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-page-object-filter-reject"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_apply_default_like_for_string_filter_on_page_route_when_enabled() throws Exception {
        defaultLikeContextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":20,\"requestId\":\"req-page-default-like\",\"filter\":{\"orderNo\":\"ORD-1\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-default-like"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.page.total").value(11))
                .andExpect(jsonPath("$.data.items.length()").value(11))
                .andExpect(jsonPath("$.data.items[0].id").value(1));
        });
    }

    @Test
    void controller_should_keep_enum_filter_as_eq_when_default_like_enabled() throws Exception {
        defaultLikeContextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":20,\"requestId\":\"req-page-enum-eq\",\"filter\":{\"status\":\"A\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-enum-eq"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.page.total").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        });
    }

    @Test
    void controller_should_respect_default_like_exclude_fields_on_page_route() throws Exception {
        defaultLikeExcludeOrderNoContextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":20,\"requestId\":\"req-page-default-like-exclude\",\"filter\":{\"orderNo\":\"ORD-1\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-page-default-like-exclude"))
                .andExpect(jsonPath("$.operation").value("PAGE"))
                .andExpect(jsonPath("$.data.page.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(1));
        });
    }

    @Test
    void controller_should_support_filter_filter_map_filter_list_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-filter-layered\",\"filter\":{\"paid\":true},\"filterMap\":{\"id\":{\"op\":\"GE\",\"value\":10}},\"filterList\":[{\"field\":\"id\",\"op\":\"LE\",\"value\":16}]},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-stats-filter-layered"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.data.mode").value("SCALAR"))
                .andExpect(jsonPath("$.data.metrics.orderCount").value(4));
        });
    }

    @Test
    void controller_should_ignore_blank_string_in_filter_and_filter_map_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-ignore-blank\",\"filter\":{\"orderNo\":\"\"},\"filterMap\":{\"status\":{\"op\":\"EQ\",\"value\":\"   \"},\"id\":{\"op\":\"GE\",\"value\":10}}},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-stats-ignore-blank"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.data.mode").value("SCALAR"))
                .andExpect(jsonPath("$.data.metrics.orderCount").value(12));
        });
    }

    @Test
    void controller_should_reject_structured_filter_value_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-structured-filter-reject\",\"filter\":{\"orderNo\":{\"op\":\"LIKE\",\"value\":\"%ORD%\"}}},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-stats-structured-filter-reject"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_reject_object_filter_value_without_op_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-object-filter-reject\",\"filter\":{\"orderNo\":{\"keyword\":\"ORD\"}}},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-stats-object-filter-reject"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_apply_default_like_for_string_filter_on_stats_route_when_enabled() throws Exception {
        defaultLikeContextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-default-like\",\"filter\":{\"orderNo\":\"ORD-1\"}},\"stats\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-stats-default-like"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.data.mode").value("SCALAR"))
                .andExpect(jsonPath("$.data.metrics.orderCount").value(11));
        });
    }

    @Test
    void controller_should_reject_legacy_payload_on_stats_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/stats")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-stats-payload-legacy\"},\"payload\":{\"metrics\":[{\"agg\":\"COUNT\",\"field\":\"id\",\"alias\":\"orderCount\"}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.requestId").value("req-stats-payload-legacy"))
                .andExpect(jsonPath("$.operationDomain").value("STATS"))
                .andExpect(jsonPath("$.operation").value("QUERY"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        });
    }

    @Test
    void controller_should_not_expose_legacy_count_route() throws Exception {
        contextRunner.run(context -> {
            MockMvc mockMvc = buildMockMvc(context);

            mockMvc.perform(post("/api/ent-crud/TestOrderEntity/count")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isNotFound());
        });
    }

    private MockMvc buildMockMvc(AssertableApplicationContext context) {
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        return MockMvcBuilders.standaloneSetup(
                context.getBean(EntCrudQueryController.class),
                context.getBean(EntCrudCommandController.class),
                context.getBean(EntCrudStatsController.class)
            )
            .setControllerAdvice(context.getBean(CrudHttpExceptionTranslator.class))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }
}
