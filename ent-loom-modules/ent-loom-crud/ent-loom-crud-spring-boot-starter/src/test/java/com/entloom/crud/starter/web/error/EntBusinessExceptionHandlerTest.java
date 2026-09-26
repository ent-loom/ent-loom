package com.entloom.crud.starter.web.error;

import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.facade.EntCrudCommandFacade;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntBusinessExceptionHandlerTest {
    @Test
    void 业务异常优先于兜底处理并保留状态码() throws Exception {
        EntBusinessException validation = new EntBusinessException("数量无效") {
            @Override
            public String getCode() {
                return "QUANTITY_INVALID";
            }
        };
        assertResponse(validation, 400, "QUANTITY_INVALID");
        EntBusinessException conflict = new EntBusinessException("订单状态冲突") {
            @Override
            public String getCode() {
                return "ORDER_CONFLICT";
            }

            @Override
            public HttpStatus getHttpStatus() {
                return HttpStatus.CONFLICT;
            }
        };
        assertResponse(conflict, 409, "ORDER_CONFLICT");
    }

    @Test
    void 未分类异常仍由兜底处理器返回内部错误() throws Exception {
        assertResponse(new IllegalStateException("执行失败"), 500, "INTERNAL_ERROR");
    }

    private void assertResponse(RuntimeException error, int expectedStatus, String expectedCode) throws Exception {
        EntCrudCommandFacade facade = mock(EntCrudCommandFacade.class);
        when(facade.action(any(), any(), any(), any())).thenThrow(error);
        CrudResponseBuilder responseBuilder = new CrudResponseBuilder();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EntCrudCommandController(facade))
            .setControllerAdvice(new CrudHttpExceptionTranslator(responseBuilder),
                new EntBusinessExceptionHandler(responseBuilder))
            .build();

        mvc.perform(post("/api/ent-crud/order/action/place")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.code").value(expectedCode));
    }
}
