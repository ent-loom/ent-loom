package com.entloom.e5.statictest;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeContributor;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.e5.statictest.fixture.CustomerProfile;
import com.entloom.e5.statictest.fixture.CustomerProfileSummary;
import com.entloom.e5.statictest.fixture.CustomerProfileSummaryQueryHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** U4：验证真实复杂查询经治理后由 Query Handler 直接返回。 */
class CustomerProfileComplexQueryHandlerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            E5CrudMvcAcceptanceTest.E5CrudMvcTestConfiguration.class,
            ComplexQueryTestConfiguration.class,
            CrudAutoConfiguration.class
        )
        .withPropertyValues(
            "ent.loom.crud.controller.enabled=true",
            "ent.loom.crud.sql-log.mode=full"
        );

    @Test
    @DisplayName("复杂摘要经 Gateway 治理后由 Handler 聚合返回")
    void should_execute_complex_summary_after_governance() {
        contextRunner.run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update(
                "insert into customer_profile(id, display_name, credit_limit, registered_at, avatar_url) "
                    + "values (?, ?, ?, ?, ?)",
                7001L,
                "范围内用户",
                new BigDecimal("88.00"),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null
            );
            jdbc.update(
                "insert into customer_profile_note(id, profile_id, created_at, content) values (?, ?, ?, ?)",
                1L,
                7001L,
                LocalDateTime.of(2026, 8, 2, 10, 0),
                "首次备注"
            );
            jdbc.update(
                "insert into customer_profile_note(id, profile_id, created_at, content) values (?, ?, ?, ?)",
                2L,
                7001L,
                LocalDateTime.of(2026, 8, 3, 10, 0),
                "最近备注"
            );

            QueryGateway gateway = context.getBean(QueryGateway.class);
            CustomerProfileSummary summary = gateway.detail(summarySpec("profile.summary", 7001L));

            assertEquals(7001L, summary.getId());
            assertEquals("范围内用户", summary.getDisplayName());
            assertEquals(2L, summary.getNoteCount());
            assertEquals(LocalDateTime.of(2026, 8, 3, 10, 0), summary.getLatestNoteAt());
            assertEquals(1, context.getBean(CustomerProfileSummaryQueryHandler.class).getHandleCalls());

            CrudGovernanceAuditEvent event = context.getBean(RecordingAuditRecorder.class).latest();
            assertEquals("customer_profile", event.getAction().getResource());
            assertEquals("DETAIL", event.getAction().getOperation());
            assertEquals("profile.summary", event.getAction().getScene());
            assertEquals("e5-test-user", event.getSubject().getSubjectId());
            assertEquals("范围内用户", event.getGovernanceScope().getDimensions().get("displayName"));
        });
    }

    @Test
    @DisplayName("复杂摘要遵循治理范围，范围外记录不可见")
    void should_not_read_profile_outside_governance_scope() {
        contextRunner.run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update(
                "insert into customer_profile(id, display_name, credit_limit, registered_at, avatar_url) "
                    + "values (?, ?, ?, ?, ?)",
                7002L,
                "范围外用户",
                new BigDecimal("88.00"),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null
            );

            CustomerProfileSummary summary = context.getBean(QueryGateway.class)
                .detail(summarySpec("profile.summary", 7002L));

            assertNull(summary);
            assertEquals(1, context.getBean(CustomerProfileSummaryQueryHandler.class).getHandleCalls());
        });
    }

    @Test
    @DisplayName("复杂摘要拒绝未声明的附加过滤条件")
    void should_reject_unsupported_filters() {
        contextRunner.run(context -> {
            List<QueryFilter> filters = new ArrayList<QueryFilter>();
            filters.add(new QueryFilter("id", FilterOperator.EQ, 7001L));
            filters.add(new QueryFilter("displayName", FilterOperator.EQ, "范围内用户"));

            assertThrows(
                com.entloom.crud.core.exception.ValidationException.class,
                () -> context.getBean(QueryGateway.class).detail(summarySpec("profile.summary", filters))
            );
            assertEquals(1, context.getBean(CustomerProfileSummaryQueryHandler.class).getHandleCalls());
        });
    }

    @Test
    @DisplayName("复杂场景未命中 Handler 时拒绝且不执行默认引擎")
    void should_reject_non_empty_scene_miss() {
        contextRunner.run(context -> {
            QueryGateway gateway = context.getBean(QueryGateway.class);
            assertThrows(
                RouteNotFoundException.class,
                () -> gateway.detail(summarySpec("profile.summary.missing", 7001L))
            );
            assertEquals(0, context.getBean(CustomerProfileSummaryQueryHandler.class).getHandleCalls());
            assertEquals("profile.summary.missing", context.getBean(RecordingAuditRecorder.class).latest().getAction().getScene());
        });
    }

    private QuerySpec<CustomerProfileSummary> summarySpec(String scene, long id) {
        return summarySpec(scene, Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, id)));
    }

    private QuerySpec<CustomerProfileSummary> summarySpec(String scene, List<QueryFilter> filters) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("e5-test-user");
        subject.setTenantId("e5-test-tenant");
        return QuerySpec.<CustomerProfileSummary>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(QueryOperation.DETAIL)
            .scene(scene)
            .filters(filters)
            .subject(subject)
            .resultType(CustomerProfileSummary.class)
            .build();
    }

    @Configuration
    static class ComplexQueryTestConfiguration {
        @Bean
        InitializingBean customerProfileNoteSchemaInitializer(JdbcTemplate jdbc) {
            return () -> {
                jdbc.execute("DROP TABLE IF EXISTS customer_profile_note");
                jdbc.execute(
                    "CREATE TABLE customer_profile_note("
                        + "id BIGINT PRIMARY KEY,"
                        + "profile_id BIGINT NOT NULL,"
                        + "created_at TIMESTAMP NOT NULL,"
                        + "content VARCHAR(255) NOT NULL"
                        + ")"
                );
            };
        }

        @Bean
        CustomerProfileSummaryQueryHandler customerProfileSummaryQueryHandler(
            JdbcTemplate jdbc,
            EntityMetaRegistry metaRegistry
        ) {
            return new CustomerProfileSummaryQueryHandler(jdbc, metaRegistry);
        }

        @Bean
        CrudDataScopeContributor customerProfileSummaryScopeContributor() {
            return new CrudDataScopeContributor() {
                @Override
                public boolean supports(CrudResourceAction action, com.entloom.crud.core.runtime.spec.BaseSpec spec) {
                    return action != null
                        && "customer_profile".equals(action.getResource())
                        && spec instanceof QuerySpec<?>
                        && "profile.summary".equals(spec.getScene());
                }

                @Override
                public CrudDataScope contribute(
                    CrudResourceAction action,
                    SubjectContext subject,
                    com.entloom.crud.core.runtime.spec.BaseSpec spec,
                    CrudDataScope grantedScope
                ) {
                    Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
                    dimensions.put("displayName", "范围内用户");
                    return CrudDataScope.scoped(dimensions);
                }
            };
        }

        @Bean
        RecordingAuditRecorder crudGovernanceAuditRecorder() {
            return new RecordingAuditRecorder();
        }
    }

    static final class RecordingAuditRecorder implements CrudGovernanceAuditRecorder {
        private final List<CrudGovernanceAuditEvent> events = new ArrayList<CrudGovernanceAuditEvent>();

        @Override
        public void record(CrudGovernanceAuditEvent event) {
            events.add(event);
        }

        CrudGovernanceAuditEvent latest() {
            return events.get(events.size() - 1);
        }
    }
}
