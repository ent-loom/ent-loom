package com.entloom.crud.starter.web.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * 将 CRUD HTTP 响应内的 Date 按框架约定格式化，不影响业务全局 ObjectMapper。
 */
public class CrudResponseDateFormatter {
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ObjectMapper objectMapper;

    public CrudResponseDateFormatter(ObjectMapper sourceObjectMapper, String timezone) {
        this.objectMapper = sourceObjectMapper == null ? new ObjectMapper() : sourceObjectMapper.copy();
        TimeZone timeZone = TimeZone.getTimeZone(
            timezone == null || timezone.trim().isEmpty() ? "Asia/Shanghai" : timezone.trim()
        );
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setTimeZone(timeZone);
        this.objectMapper.setDateFormat(new SimpleDateFormat(DEFAULT_DATE_TIME_PATTERN));
    }

    public Object format(Object body) {
        if (body == null || body instanceof CharSequence || body instanceof byte[]) {
            return body;
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignore) {
            return body;
        }
    }
}
