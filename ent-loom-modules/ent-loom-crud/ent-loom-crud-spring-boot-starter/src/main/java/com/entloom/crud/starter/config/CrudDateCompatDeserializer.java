package com.entloom.crud.starter.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.TimeZone;

/**
 * Date 兼容反序列化器：
 * 1) ISO-8601（StdDateFormat）；
 * 2) yyyy-MM-dd HH:mm:ss；
 * 3) yyyy-MM-dd。
 */
final class CrudDateCompatDeserializer extends JsonDeserializer<Date> {
    private static final DateTimeFormatter LEGACY_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LEGACY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TimeZone mapperTimeZone;
    private final ZoneId legacyZoneId;

    CrudDateCompatDeserializer(TimeZone mapperTimeZone, ZoneId legacyZoneId) {
        this.mapperTimeZone = mapperTimeZone;
        this.legacyZoneId = legacyZoneId;
    }

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return new Date(parser.getLongValue());
        }
        if (token != JsonToken.VALUE_STRING) {
            return (Date) context.handleUnexpectedToken(Date.class, parser);
        }
        String raw = parser.getText();
        String text = raw == null ? null : raw.trim();
        if (text == null || text.isEmpty()) {
            return null;
        }

        Date parsed = tryParseIso(text);
        if (parsed != null) {
            return parsed;
        }
        parsed = tryParseLegacyDateTime(text);
        if (parsed != null) {
            return parsed;
        }
        parsed = tryParseLegacyDate(text);
        if (parsed != null) {
            return parsed;
        }

        throw InvalidFormatException.from(
            parser,
            "not a valid representation (supported: ISO-8601, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd)",
            text,
            Date.class
        );
    }

    private Date tryParseIso(String text) {
        DateFormat dateFormat = new StdDateFormat().withColonInTimeZone(true);
        if (mapperTimeZone != null) {
            dateFormat.setTimeZone(mapperTimeZone);
        }
        try {
            return dateFormat.parse(text);
        } catch (ParseException ex) {
            return null;
        }
    }

    private Date tryParseLegacyDateTime(String text) {
        try {
            LocalDateTime value = LocalDateTime.parse(text, LEGACY_DATE_TIME_FORMATTER);
            return Date.from(value.atZone(legacyZoneId).toInstant());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Date tryParseLegacyDate(String text) {
        try {
            LocalDate value = LocalDate.parse(text, LEGACY_DATE_FORMATTER);
            return Date.from(value.atStartOfDay(legacyZoneId).toInstant());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
