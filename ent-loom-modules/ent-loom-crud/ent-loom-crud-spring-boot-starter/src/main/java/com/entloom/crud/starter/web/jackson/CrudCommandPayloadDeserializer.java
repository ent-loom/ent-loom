package com.entloom.crud.starter.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;

/** 通用命令的十进制数直接读取为 BigDecimal，避免金额先经 Double 丢失精度。 */
public final class CrudCommandPayloadDeserializer extends JsonDeserializer<Object> {
    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        ObjectReader reader = codec instanceof ObjectReader ? ((ObjectReader) codec).forType(Object.class)
            : ((ObjectMapper) codec).readerFor(Object.class);
        return reader.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(parser);
    }
}
