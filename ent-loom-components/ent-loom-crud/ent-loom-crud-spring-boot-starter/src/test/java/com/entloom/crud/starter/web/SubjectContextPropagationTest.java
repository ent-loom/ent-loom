package com.entloom.crud.starter.web;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.starter.support.TestOrderEntity;
import com.entloom.crud.starter.web.assembler.CrudCommandSpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudQuerySpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudStatsSpecAssembler;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.dto.CrudReadHttpRequest;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import com.entloom.crud.starter.web.registry.ExposedViewTypeRegistry;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.entloom.crud.starter.web.time.CrudTimeFilterResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectContextPropagationTest {

    @Test
    void query_spec_should_keep_full_subject_context() {
        CrudQuerySpecAssembler assembler = new CrudQuerySpecAssembler(
            requestSupport(),
            new CrudTimeFilterResolver("UTC"),
            new ObjectMapper()
        );

        SubjectContext subject = subject("test-user", "test-tenant", "test-org");
        QuerySpec<Object> spec = assembler.assembleRead(
            QueryOperation.PAGE,
            "TestOrderEntity",
            null,
            new CrudReadHttpRequest(),
            subject
        );

        assertThat(spec.getSubject()).isNotSameAs(subject);
        assertThat(spec.getSubject().getSubjectId()).isEqualTo("test-user");
        assertThat(spec.getSubject().getTenantId()).isEqualTo("test-tenant");
        assertThat(spec.getSubject().getOrgId()).isEqualTo("test-org");
    }

    @Test
    void command_spec_should_keep_full_subject_context() {
        CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            new ObjectMapper()
        );

        SubjectContext subject = subject("test-user", "test-tenant", "test-org");
        CommandSpec<Object> spec = assembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            new CrudCommandHttpRequest(),
            subject
        );

        assertThat(spec.getSubject()).isNotSameAs(subject);
        assertThat(spec.getSubject().getSubjectId()).isEqualTo("test-user");
        assertThat(spec.getSubject().getTenantId()).isEqualTo("test-tenant");
        assertThat(spec.getSubject().getOrgId()).isEqualTo("test-org");
    }

    @Test
    void command_spec_should_keep_single_write_payload_raw_for_scene_handler() {
        CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            new ObjectMapper()
        );
        CrudCommandHttpRequest request = new CrudCommandHttpRequest();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("orderNo", "ORD-1");
        request.setPayload(payload);

        CommandSpec<Object> spec = assembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            request,
            subject("test-user", "test-tenant", "test-org")
        );

        assertThat(spec.getPayload()).isSameAs(payload);
        assertThat(spec.getPayload()).isNotInstanceOf(WriteCommand.class);
    }

    @Test
    void command_request_should_bind_structured_options_and_reject_extra_options() throws Exception {
        CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            new ObjectMapper()
        );
        CrudCommandHttpRequest request = new ObjectMapper().readValue(
            "{\"options\":{\"requestId\":\"req-cmd-1\",\"idempotencyKey\":\"idem-1\",\"expectedVersion\":12,\"dryRun\":true,\"clientTag\":\"web\"}}",
            CrudCommandHttpRequest.class
        );

        assertThat(request.getOptions().getRequestId()).isEqualTo("req-cmd-1");
        assertThat(request.getOptions().getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(request.getOptions().getExpectedVersion()).isEqualTo(12L);
        assertThat(request.getOptions().isDryRunEnabled()).isTrue();
        assertThatThrownBy(() -> assembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            request,
            subject("test-user", "test-tenant", "test-org")
        )).hasMessageContaining("options.clientTag 不支持");
    }

    @Test
    void command_spec_should_reject_unsupported_top_level_fields() throws Exception {
        CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            new ObjectMapper()
        );
        CrudCommandHttpRequest request = new ObjectMapper().readValue(
            "{\"payload\":{\"orderNo\":\"ORD-1\"},\"clientTag\":\"web\"}",
            CrudCommandHttpRequest.class
        );

        assertThatThrownBy(() -> assembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            request,
            subject("test-user", "test-tenant", "test-org")
        )).hasMessageContaining("command 路由 不支持顶层字段");
    }

    @Test
    void command_spec_should_reject_scene_in_command_options() throws Exception {
        CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            new ObjectMapper()
        );
        CrudCommandHttpRequest request = new ObjectMapper().readValue(
            "{\"options\":{\"scene\":\"bad.scene\"}}",
            CrudCommandHttpRequest.class
        );

        assertThatThrownBy(() -> assembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            request,
            subject("test-user", "test-tenant", "test-org")
        )).hasMessageContaining("options.scene 不允许出现");
    }

    @Test
    void read_command_and_stats_should_reject_attribute_context_options() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CrudQuerySpecAssembler queryAssembler = new CrudQuerySpecAssembler(
            requestSupport(),
            new CrudTimeFilterResolver("UTC"),
            objectMapper
        );
        CrudCommandSpecAssembler commandAssembler = new CrudCommandSpecAssembler(
            requestSupport(),
            Mockito.mock(CommandActionSceneResolver.class),
            metaRegistry(),
            objectMapper
        );
        CrudStatsSpecAssembler statsAssembler = new CrudStatsSpecAssembler(
            requestSupport(),
            new CrudTimeFilterResolver("UTC"),
            objectMapper
        );
        SubjectContext subject = subject("test-user", "test-tenant", "test-org");
        CrudReadHttpRequest readRequest = objectMapper.readValue(
            "{\"options\":{\"Attrs\":{\"traceId\":\"bad\"}}}",
            CrudReadHttpRequest.class
        );
        CrudCommandHttpRequest commandRequest = objectMapper.readValue(
            "{\"options\":{\"crudExplicitAll\":true}}",
            CrudCommandHttpRequest.class
        );
        CrudStatsHttpRequest statsRequest = objectMapper.readValue(
            "{\"options\":{\"context\":{\"tenantId\":\"bad\"}}}",
            CrudStatsHttpRequest.class
        );

        assertThatThrownBy(() -> queryAssembler.assembleRead(
            QueryOperation.PAGE,
            "TestOrderEntity",
            null,
            readRequest,
            subject
        )).hasMessageContaining("CrudSpecAttributeContributor");
        assertThatThrownBy(() -> commandAssembler.assemble(
            "TestOrderEntity",
            CommandOperation.CREATE,
            null,
            commandRequest,
            subject
        )).hasMessageContaining("CrudSpecAttributeContributor");
        assertThatThrownBy(() -> statsAssembler.assembleStats(
            "TestOrderEntity",
            null,
            statsRequest,
            subject
        )).hasMessageContaining("CrudSpecAttributeContributor");
    }

    private CrudRequestSupport requestSupport() {
        ExposedEntityRegistry entityRegistry = new ExposedEntityRegistry();
        entityRegistry.expose(TestOrderEntity.class);
        return new CrudRequestSupport(entityRegistry, new ExposedViewTypeRegistry());
    }

    private EntityMetaRegistry metaRegistry() {
        return new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser()
                .parse(java.util.Collections.<Class<?>>singletonList(TestOrderEntity.class))
        );
    }

    private SubjectContext subject(String subjectId, String tenantId, String orgId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        subject.setTenantId(tenantId);
        subject.setOrgId(orgId);
        return subject;
    }
}
