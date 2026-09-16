package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudNullFieldMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
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
        TestEnvelope envelope = TestEnvelope.item(new TestRecord(parseShanghaiTime("2026-04-20 11:46:42")));

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
    void format_should_omit_null_fields_recursively() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        TestEnvelope envelope = TestEnvelope.item(new TestRecord(null));

        Object formatted = formatter.format(envelope, CrudNullFieldMode.OMIT);
        String json = new ObjectMapper().writeValueAsString(formatted);

        assertThat(json).isEqualTo("{\"message\":null,\"data\":{\"item\":{}}}");
    }

    @Test
    void format_should_include_null_fields_when_query_overrides_default() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        TestEnvelope envelope = TestEnvelope.item(new TestRecord(null));

        Object formatted = formatter.format(envelope, CrudNullFieldMode.INCLUDE);
        String json = new ObjectMapper().writeValueAsString(formatted);

        assertThat(json).isEqualTo("{\"message\":null,\"data\":{\"item\":{\"startedTime\":null}}}");
    }

    @Test
    void format_should_keep_null_protocol_field_when_item_is_missing() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        TestEnvelope envelope = TestEnvelope.item(null);

        Object formatted = formatter.format(envelope, CrudNullFieldMode.OMIT);
        String json = new ObjectMapper().writeValueAsString(formatted);

        assertThat(json).isEqualTo("{\"message\":null,\"data\":{\"item\":null}}");
    }

    @Test
    void format_should_only_omit_record_null_fields_in_business_wrapper() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        record.put("name", "测试记录");
        record.put("remark", null);
        Map<String, Object> page = new LinkedHashMap<String, Object>();
        page.put("hasNext", null);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("items", Arrays.<Object>asList(record));
        payload.put("page", page);
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("msg", null);
        envelope.put("items", payload);

        Object formatted = formatter.format(envelope, CrudNullFieldMode.OMIT);
        String json = new ObjectMapper().writeValueAsString(formatted);

        assertThat(json).isEqualTo(
            "{\"msg\":null,\"items\":{\"items\":[{\"name\":\"测试记录\"}],\"page\":{\"hasNext\":null}}}"
        );
    }

    @Test
    void advice_should_format_only_ent_crud_path() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        CrudResponseDateFormatAdvice advice = new CrudResponseDateFormatAdvice(formatter);
        TestEnvelope envelope = TestEnvelope.item(new TestRecord(parseShanghaiTime("2026-04-20 11:46:42")));

        Object crudBody = advice.beforeBodyWrite(
            envelope,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            request("/busCenter/api/ent-crud/BusImportTaskRecord/page"),
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

    @Test
    void advice_should_use_configured_base_path() throws Exception {
        CrudResponseDateFormatter formatter = new CrudResponseDateFormatter(new ObjectMapper(), "Asia/Shanghai");
        CrudResponseDateFormatAdvice advice = new CrudResponseDateFormatAdvice(formatter, "/api/custom-crud/");
        TestEnvelope envelope = TestEnvelope.item(new TestRecord(parseShanghaiTime("2026-04-20 11:46:42")));

        Object customBody = advice.beforeBodyWrite(
            envelope,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            request("/busCenter/api/custom-crud/TestOrderEntity/page"),
            null
        );
        Object unrelatedBody = advice.beforeBodyWrite(
            envelope,
            null,
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
            request("/busCenter/api/custom-crudish/TestOrderEntity/page"),
            null
        );

        assertThat(new ObjectMapper().writeValueAsString(customBody))
            .contains("\"startedTime\":\"2026-04-20 11:46:42\"");
        assertThat(unrelatedBody).isSameAs(envelope);
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
        private final String message;
        private final TestData data;

        private TestEnvelope(String message, TestData data) {
            this.message = message;
            this.data = data;
        }

        private static TestEnvelope item(TestRecord item) {
            return new TestEnvelope(null, new TestData(item));
        }

        public String getMessage() {
            return message;
        }

        public TestData getData() {
            return data;
        }
    }

    private static class TestData {
        private final TestRecord item;

        private TestData(TestRecord item) {
            this.item = item;
        }

        public TestRecord getItem() {
            return item;
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
