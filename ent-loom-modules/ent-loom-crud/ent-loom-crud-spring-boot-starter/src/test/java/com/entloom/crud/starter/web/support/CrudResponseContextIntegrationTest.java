package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudErrorEnvelope;
import com.entloom.crud.api.model.CrudResponse;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrudResponseContextIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(
                new CrudResponseDateFormatAdvice(new CrudResponseDateFormatter(mapper, "Asia/Shanghai")),
                new CrudResponseContextAdvice(new CrudResponseEnhancer()))
            .setMessageConverters(new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(mapper))
            .addFilters(new CrudHttpRequestContextFilter())
            .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 静态工厂只创建基础响应并复制错误信息() {
        CrudErrorEnvelope error = new CrudErrorEnvelope("INVALID", "无效", null, null, "INVALID");
        error.setRequestId("original-request");
        CrudResponse<Void> response = CrudResponse.failure(error);

        assertThat(response.getRequestId()).isNull();
        assertThat(response.getTraceId()).isNull();
        assertThat(response.getError()).isNotSameAs(error);
        assertThat(response.getError().getStage()).isEqualTo(CrudErrorStage.UNKNOWN);
        assertThat(error.getStage()).isNull();
        assertThat(response.getError().getRequestId()).isEqualTo("original-request");
    }

    @Test
    void 成功响应先补充上下文再格式化且不影响下一个请求() throws Exception {
        MDC.put("traceId", "trace-success");
        MvcResult result = mvc.perform(get("/api/ent-crud/test/success"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.traceId").value("trace-success"))
            .andExpect(jsonPath("$.operationDomain").value("QUERY"))
            .andExpect(jsonPath("$.operation").value("PAGE"))
            .andExpect(jsonPath("$.data.time").value("1970-01-01 08:00:00"))
            .andReturn();
        String firstRequestId = body(result).path("requestId").asText();
        assertThat(CrudHttpRequestContext.current()).isNull();

        MvcResult next = mvc.perform(get("/api/ent-crud/test/plain"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operation").doesNotExist())
            .andReturn();
        assertThat(body(next).path("requestId").asText()).isNotEqualTo(firstRequestId);
    }

    @Test
    void 异常响应的内外请求标识保持一致() throws Exception {
        MDC.put("traceId", "trace-error");
        JsonNode body = body(mvc.perform(get("/api/ent-crud/test/error"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.operation").value("DETAIL"))
            .andReturn());

        assertThat(body.path("requestId").asText()).isNotBlank();
        assertThat(body.path("error").path("requestId").asText()).isEqualTo(body.path("requestId").asText());
        assertThat(body.path("traceId").asText()).isEqualTo("trace-error");
        assertThat(body.path("error").path("traceId").asText()).isEqualTo("trace-error");
    }

    @Test
    void callable中的操作信息在异步分派后仍可用于响应() throws Exception {
        MDC.put("traceId", "trace-async");
        MvcResult initial = mvc.perform(get("/api/ent-crud/test/async"))
            .andExpect(request().asyncStarted()).andReturn();
        initial.getAsyncResult(5000);
        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.traceId").value("trace-async"))
            .andExpect(jsonPath("$.operationDomain").value("QUERY"))
            .andExpect(jsonPath("$.operation").value("LIST"));
        assertThat(CrudHttpRequestContext.current()).isNull();
    }

    @Test
    void 二进制响应不被包装() throws Exception {
        mvc.perform(get("/api/ent-crud/test/file"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void Jackson包装响应保留过滤规则与请求标识() throws Exception {
        MDC.put("traceId", "trace-wrapped");
        mvc.perform(get("/api/ent-crud/test/wrapped"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.traceId").value("trace-wrapped"))
            .andExpect(jsonPath("$.data.visible").value("ok"))
            .andExpect(jsonPath("$.data.secret").doesNotExist())
            .andExpect(jsonPath("$.data.time").value("1970-01-01 08:00:00"));
    }

    @Test
    void Jackson包装响应保留方法声明的视图() throws Exception {
        mvc.perform(get("/api/ent-crud/test/wrapped-view"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(jsonPath("$.data.visible").value("ok"))
            .andExpect(jsonPath("$.data.secret").doesNotExist())
            .andExpect(jsonPath("$.data.time").value("1970-01-01 08:00:00"));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @RestController
    static class TestController {
        @GetMapping("/api/ent-crud/test/success")
        CrudResponse<Map<String, Date>> success() {
            CrudHttpRequestContext.bindOperation(CrudOperationKey.of(QueryOperation.PAGE));
            return CrudResponse.success(Map.of("time", new Date(0)));
        }

        @GetMapping("/api/ent-crud/test/plain")
        CrudResponse<String> plain() {
            return CrudResponse.success("ok");
        }

        @GetMapping("/api/ent-crud/test/error")
        CrudResponse<Void> error() {
            CrudHttpRequestContext.bindOperation(CrudOperationKey.of(QueryOperation.DETAIL));
            throw new IllegalStateException("测试异常");
        }

        @ExceptionHandler(IllegalStateException.class)
        ResponseEntity<CrudResponse<Void>> handleError() {
            return ResponseEntity.badRequest().body(CrudResponse.failure("INVALID", "请求无效"));
        }

        @GetMapping("/api/ent-crud/test/async")
        Callable<CrudResponse<String>> async() {
            return () -> {
                CrudHttpRequestContext.bindOperation(CrudOperationKey.of(QueryOperation.LIST));
                return CrudResponse.success("ok");
            };
        }

        @GetMapping(value = "/api/ent-crud/test/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        byte[] file() {
            return new byte[]{1, 2, 3};
        }

        @GetMapping("/api/ent-crud/test/wrapped")
        MappingJacksonValue wrapped() {
            MappingJacksonValue value = new MappingJacksonValue(CrudResponse.success(new FilteredData()));
            value.setFilters(new SimpleFilterProvider()
                .addFilter("visible", SimpleBeanPropertyFilter.serializeAllExcept("secret")));
            return value;
        }

        @JsonView(PublicView.class)
        @GetMapping("/api/ent-crud/test/wrapped-view")
        MappingJacksonValue wrappedView() {
            MappingJacksonValue value = new MappingJacksonValue(CrudResponse.success(new FilteredData()));
            value.setFilters(new SimpleFilterProvider()
                .addFilter("visible", SimpleBeanPropertyFilter.serializeAll()));
            return value;
        }
    }

    interface PublicView {}

    interface InternalView extends PublicView {}

    @JsonFilter("visible")
    static class FilteredData {
        @JsonView(PublicView.class)
        public String visible = "ok";
        @JsonView(InternalView.class)
        public String secret = "hidden";
        @JsonView(PublicView.class)
        public Date time = new Date(0);
    }
}
