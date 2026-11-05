package com.example.minicommerce.common.web;

import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.starter.web.error.EntBusinessExceptionHandler;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.order.enums.CommerceOrderError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 经真实 MVC 异常解析链验证响应格式、Advice 优先级和 HTTP 语义。 */
class ApiExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        var responses = new CrudResponseBuilder();
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new ApiExceptionHandler(responses), new EntBusinessExceptionHandler(responses))
            .build();
    }

    @Test
    void 框架权限异常保持状态与错误信封() throws Exception {
        mvc.perform(get("/test/errors/permission"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @ParameterizedTest
    @CsvSource({
        "business, 400, ITEMS_REQUIRED, 订单至少需要一件商品"
    })
    void 公共业务异常由框架全局处理(String kind, int httpStatus, String code, String message)
        throws Exception {
        mvc.perform(get("/test/errors/" + kind))
            .andExpect(status().is(httpStatus))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void 分页校验复用同一处理器() throws Exception {
        mvc.perform(post("/test/page").contentType(MediaType.APPLICATION_JSON).content("{\"pageNumber\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("页码须从 1 开始"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"pageNumber\":\"错误类型\"}"})
    void 请求体缺失或解析失败返回统一提示(String body) throws Exception {
        mvc.perform(post("/test/page").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("请求体不能为空，且须为符合接口约定的 JSON"));
    }

    @Test
    void 未匹配控制器的方法错误仍保留405与Allow响应头() throws Exception {
        mvc.perform(get("/test/page"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST"));
    }

    @Test
    void 内容类型不支持时保持415状态() throws Exception {
        mvc.perform(post("/test/page").contentType(MediaType.TEXT_PLAIN).content("{}"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "annotated-status"})
    void 显式HTTP异常保持原始状态(String kind) throws Exception {
        mvc.perform(get("/test/errors/" + kind))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("请求资源不存在"));
    }

    @Test
    void 未知异常返回通用提示且不暴露内部消息() throws Exception {
        mvc.perform(get("/test/errors/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"))
            .andExpect(jsonPath("$.error.message").value("服务暂时不可用，请稍后重试"));
    }

    // 非静态测试控制器不参与应用组件扫描，仅供 standalone MVC 注册。
    @RestController
    class TestController {
        @PostMapping("/test/page")
        void page(@Valid @RequestBody TestPageQuery query) {}

        @GetMapping("/test/errors/{kind}")
        void error(@PathVariable String kind) {
            throw switch (kind) {
                case "permission" -> new PermissionDeniedException("无权访问");
                case "business" -> new BusinessException(CommerceOrderError.ITEMS_REQUIRED);
                case "status" -> new ResponseStatusException(HttpStatus.NOT_FOUND, "内部资源定位信息");
                case "annotated-status" -> new MissingResourceException();
                default -> new IllegalStateException("内部 SQL 与连接信息");
            };
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class MissingResourceException extends RuntimeException {}

    record TestPageQuery(@Min(value = 1, message = "页码须从 1 开始") int pageNumber) {}
}
