package com.example.minicommerce.order;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.permission.CrudPermissionRule;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.permission.RuleBasedCrudPermissionService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.governance.subject.FailClosedCrudSubjectResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Gateway 管线的授权回归，只替换主体与权限实现。 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:commerce-access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always"
})
@ActiveProfiles("example")
@AutoConfigureMockMvc
class OrderAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CrudSubjectResolver subjectResolver;
    @MockitoBean CrudPermissionService permissionService;

    @BeforeEach
    void prepare() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("local-developer");
        when(subjectResolver.resolveOrThrow()).thenReturn(subject);
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
    }

    @Test
    void missingRulesRejectBothHttpEntrypoints() throws Exception {
        rules(List.of());
        assertDenied();
    }

    @Test
    void explicitDenyRejectsBothHttpEntrypoints() throws Exception {
        rules(List.of(rule("*", "*", AccessDecision.DENY)));
        assertDenied();
    }

    @Test
    void unknownSubjectCannotUseDeveloperRules() throws Exception {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("other-user");
        when(subjectResolver.resolveOrThrow()).thenReturn(subject);
        rules(List.of(rule("*", "*", AccessDecision.ALLOW)));
        assertDenied();
    }

    @Test
    void missingSubjectFailsClosed() throws Exception {
        when(subjectResolver.resolveOrThrow()).thenReturn(null);
        assertRejected(status().isBadRequest(), "VALIDATION_ERROR");
    }

    @Test
    void defaultSubjectResolverFailsClosed() throws Exception {
        when(subjectResolver.resolveOrThrow()).thenAnswer(invocation -> new FailClosedCrudSubjectResolver().resolveOrThrow());
        assertRejected(status().isBadRequest(), "VALIDATION_ERROR");
    }

    @Test
    void permissionIsSpecificToOperationAndScene() throws Exception {
        rules(List.of(rule("COMMAND:ACTION", "place", AccessDecision.ALLOW)));
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON).content(command()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mvc.perform(post("/api/ent-crud/order/detail/detail").contentType(MediaType.APPLICATION_JSON).content(detail()))
            .andExpect(status().isForbidden());
        rules(List.of(rule("COMMAND:ACTION", "other-scene", AccessDecision.ALLOW)));
        assertDenied();
    }

    @Test
    void invalidCommandIsRejectedByHandlerAfterPermission() throws Exception {
        rules(List.of(rule("COMMAND:ACTION", "place", AccessDecision.ALLOW)));
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
            .content("{\"payload\":{\"customerId\":2001,\"items\":[{\"productId\":1001,\"quantity\":0}]}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITY_INVALID"));
        verify(permissionService).decide(any(), any(), any());
    }

    private void assertDenied() throws Exception {
        assertRejected(status().isForbidden(), "PERMISSION_DENIED");
    }

    private void assertRejected(org.springframework.test.web.servlet.ResultMatcher status, String code) throws Exception {
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON).content(command()))
            .andExpect(status).andExpect(jsonPath("$.code").value(code));
        mvc.perform(post("/api/ent-crud/order/detail/detail").contentType(MediaType.APPLICATION_JSON).content(detail()))
            .andExpect(status).andExpect(jsonPath("$.code").value(code));
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
    }

    private void rules(List<CrudPermissionRule> rules) {
        var service = new RuleBasedCrudPermissionService(rules);
        doAnswer(invocation -> service.decide(invocation.getArgument(0), invocation.getArgument(1),
            invocation.getArgument(2))).when(permissionService).decide(any(), any(), any());
    }

    private CrudPermissionRule rule(String action, String scene, AccessDecision decision) {
        return new CrudPermissionRule("order", action, scene, decision, Set.of("local-developer"), Set.of(), Set.of());
    }

    private String command() {
        return "{\"payload\":{\"customerId\":2001,\"items\":[{\"productId\":1001,\"quantity\":1}]}}";
    }

    private String detail() {
        return "{\"options\":{\"filter\":{\"id\":1}}}";
    }
}
