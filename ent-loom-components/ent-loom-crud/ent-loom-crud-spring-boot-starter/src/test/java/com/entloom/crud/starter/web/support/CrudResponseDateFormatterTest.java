package com.entloom.crud.starter.web.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CrudResponseDateFormatterTest {

    @Test
    void format_should_convert_nested_date_to_text_without_global_mapper() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        TestEnvelope envelope = new TestEnvelope(new TestRecord(parseShanghaiTime("2026-04-20 11:46:42")));

        Object formatted = formatter.format(envelope);
        String json = new ObjectMapper().writeValueAsString(formatted);

        assertThat(json).contains("\"startedTime\":\"2026-04-20 11:46:42\"");
        assertThat(json).doesNotContain("\"startedTime\":1776656802000");
    }

    @Test
    void format_should_leave_binary_response_untouched() {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        byte[] content = new byte[] {1, 2, 3};

        assertThat(formatter.format(content)).isSameAs(content);
    }

    @Test
    void advice_should_format_only_ent_crud_path() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        CrudResponseDateFormatAdvice advice = new CrudResponseDateFormatAdvice(formatter);
        TestEnvelope envelope = new TestEnvelope(new TestRecord(parseShanghaiTime("2026-04-20 11:46:42")));

        Object crudBody = advice.beforeBodyWrite(
            envelope,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            request("/busCenter/api/base/ent-crud/BusImportTaskRecord/page"),
            null
        );
        Object legacyBody = advice.beforeBodyWrite(
            envelope,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            request("/busCenter/legacy/page"),
            null
        );

        assertThat(new ObjectMapper().writeValueAsString(crudBody))
            .contains("\"startedTime\":\"2026-04-20 11:46:42\"");
        assertThat(legacyBody).isSameAs(envelope);
    }

    private ServletServerHttpRequest request(String path) {
        return new ServletServerHttpRequest(new MockHttpServletRequest("POST", path));
    }

    private Date parseShanghaiTime(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return format.parse(value);
    }

    private static class TestEnvelope {
        private final TestRecord items;

        private TestEnvelope(TestRecord items) {
            this.items = items;
        }

        public TestRecord getItems() {
            return items;
        }
    }

    private static class TestRecord {
        private final Date startedTime;

        private TestRecord(Date startedTime) {
            this.startedTime = startedTime;
        }

        public Date getStartedTime() {
            return startedTime;
        }
    }
}
