package com.entloom.crud.starter.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;

class CrudHttpRequestContextFilterTest {
    @AfterEach void clearMdc() { MDC.clear(); }

    @Test
    void 请求与错误分派复用标识并恢复线程上下文() throws Exception {
        CrudHttpRequestContextFilter filter = new CrudHttpRequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("requestId", "previous-request");
        MDC.put("traceId", "trace-1");
        AtomicReference<CrudHttpRequestContext> first = new AtomicReference<>();
        CrudHttpRequestAttributeContributor contributor = new CrudHttpRequestAttributeContributor();
        var resolver = new DefaultCrudSpecAttributeResolver(List.of(contributor), contributor.reservedAttributeKeys());
        var spec = QuerySpec.builder().rootType(Object.class).resultType(Object.class)
            .attributes(Map.of("requestId", "saved-request", "traceId", "saved-trace")).build();
        assertThat(contributor.contribute(null)).isEmpty();
        assertThat(contributor.reservedAttributeKeys()).containsExactly("requestId", "traceId");
        assertThat(resolver.resolve(spec)).containsEntry("requestId", "saved-request")
            .containsEntry("traceId", "saved-trace");
        filter.doFilter(request, response, (req, res) -> {
            first.set(CrudHttpRequestContext.current());
            assertThat(first.get().getRequestId()).isNotBlank().isNotEqualTo("previous-request");
            assertThat(first.get().getTraceId()).isEqualTo("trace-1");
            assertThat(MDC.get("requestId")).isEqualTo(first.get().getRequestId());
            assertThat(contributor.contribute(null)).containsEntry("requestId", first.get().getRequestId())
                .containsEntry("traceId", "trace-1");
            assertThat(resolver.resolve(spec)).containsEntry("requestId", first.get().getRequestId())
                .containsEntry("traceId", "trace-1");
        });
        assertThat(CrudHttpRequestContext.current()).isNull();
        assertThat(MDC.get("requestId")).isEqualTo("previous-request");
        MDC.put("traceId", "other-trace");
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> {
            assertThat(CrudHttpRequestContext.current()).isSameAs(first.get());
            assertThat(MDC.get("traceId")).isEqualTo("trace-1");
        });
        assertThat(MDC.get("traceId")).isEqualTo("other-trace");
        assertThat(CrudHttpRequestContext.current()).isNull();
    }

    @Test
    void 异步工作线程复用请求标识并清理() throws Exception {
        CrudHttpRequestContextFilter filter = new CrudHttpRequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        AtomicReference<CrudHttpRequestContext> context = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> context.set(CrudHttpRequestContext.current()));
        CallableProcessingInterceptor interceptor = WebAsyncUtils.getAsyncManager(request)
            .getCallableInterceptor(CrudHttpRequestContextFilter.class.getName());
        Callable<String> task = () -> CrudHttpRequestContext.current().getRequestId();
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertThat(executor.submit(() -> {
                ServletWebRequest webRequest = new ServletWebRequest(request);
                interceptor.preProcess(webRequest, task);
                try { return task.call(); }
                finally { interceptor.postProcess(webRequest, task, null); }
            }).get()).isEqualTo(context.get().getRequestId());
            assertThat(executor.submit(CrudHttpRequestContext::current).get()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
