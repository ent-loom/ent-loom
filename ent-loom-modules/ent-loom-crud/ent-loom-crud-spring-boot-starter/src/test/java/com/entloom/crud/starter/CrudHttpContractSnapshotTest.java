package com.entloom.crud.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import com.entloom.crud.starter.web.error.CrudHttpExceptionTranslator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD HTTP 成功与失败响应快照合同。
 */
class CrudHttpContractSnapshotTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, CrudAutoConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full"
        );

    @Test
    void success_response_should_match_json_snapshot() throws Exception {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            MvcResult result = buildMockMvc(context)
                .perform(post("/api/ent-crud/TestOrderEntity/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"requestId\":\"req-snapshot-success\"},\"payload\":{\"id\":1001,\"orderNo\":\"ORD-SNAPSHOT\",\"deleted\":0}}"))
                .andExpect(status().isOk())
                .andReturn();

            assertSnapshot("snapshots/crud-success.json", result, objectMapper);
        });
    }

    @Test
    void failure_response_should_match_json_snapshot() throws Exception {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            MvcResult result = buildMockMvc(context)
                .perform(post("/api/ent-crud/TestOrderEntity/page")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"options\":{\"page\":1,\"limit\":2,\"requestId\":\"req-snapshot-failure\",\"resultMode\":\"DTO\"}}"))
                .andExpect(status().isBadRequest())
                .andReturn();

            assertSnapshot("snapshots/crud-failure.json", result, objectMapper);
        });
    }

    private void assertSnapshot(String resource, MvcResult result, ObjectMapper objectMapper) throws IOException {
        JsonNode actual = objectMapper.readTree(result.getResponse().getContentAsString());
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            Assertions.assertNotNull(input, "缺少 JSON snapshot: " + resource);
            String expectedJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode expected = objectMapper.readTree(expectedJson);
            Assertions.assertEquals(expected, actual, "JSON snapshot 不一致: " + resource);
        }
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
