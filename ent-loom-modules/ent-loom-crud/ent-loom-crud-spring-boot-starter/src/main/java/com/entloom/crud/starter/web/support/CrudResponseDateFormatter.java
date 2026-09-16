package com.entloom.crud.starter.web.support;

import com.entloom.crud.api.enums.CrudNullFieldMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

/**
 * 格式化 CRUD HTTP 响应日期，并按查询选项清理记录内的空字段。
 */
public class CrudResponseDateFormatter {
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ObjectMapper objectMapper;
    private final ObjectMapper includeNullObjectMapper;

    public CrudResponseDateFormatter(ObjectMapper sourceObjectMapper, String timezone) {
        ObjectMapper objectMapper = sourceObjectMapper == null ? new ObjectMapper() : sourceObjectMapper.copy();
        TimeZone timeZone = TimeZone.getTimeZone(
            timezone == null || timezone.trim().isEmpty() ? "Asia/Shanghai" : timezone.trim()
        );
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setTimeZone(timeZone);
        objectMapper.setDateFormat(new SimpleDateFormat(DEFAULT_DATE_TIME_PATTERN));
        this.includeNullObjectMapper = objectMapper.copy();
        this.includeNullObjectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        this.objectMapper = objectMapper;
    }

    public Object format(Object body) {
        return format(body, CrudNullFieldMode.INCLUDE);
    }

    public Object format(Object body, CrudNullFieldMode nullFieldMode) {
        if (body == null || body instanceof CharSequence || body instanceof byte[]) {
            return body;
        }
        try {
            ObjectMapper formatterMapper = nullFieldMode == CrudNullFieldMode.OMIT
                ? objectMapper
                : includeNullObjectMapper;
            JsonNode root = formatterMapper.readTree(formatterMapper.writeValueAsBytes(body));
            if (nullFieldMode == CrudNullFieldMode.OMIT) {
                omitRecordNullFields(root);
            }
            return formatterMapper.treeToValue(root, Object.class);
        } catch (Exception ignore) {
            return body;
        }
    }

    private void omitRecordNullFields(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            omitReadPayloadNullFields(data);
        }
        JsonNode items = root.get("items");
        if (items != null && items.isObject()) {
            omitReadPayloadNullFields(items);
        }
    }

    private void omitReadPayloadNullFields(JsonNode payload) {
        JsonNode item = payload.get("item");
        if (item != null && !item.isNull()) {
            omitNullFields(item);
        }
        JsonNode items = payload.get("items");
        if (items != null && items.isArray()) {
            for (JsonNode record : items) {
                if (record != null && !record.isNull()) {
                    omitNullFields(record);
                }
            }
        }
    }

    private void omitNullFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value == null || value.isNull()) {
                    fields.remove();
                } else {
                    omitNullFields(value);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode value : node) {
                if (value != null && !value.isNull()) {
                    omitNullFields(value);
                }
            }
        }
    }
}
