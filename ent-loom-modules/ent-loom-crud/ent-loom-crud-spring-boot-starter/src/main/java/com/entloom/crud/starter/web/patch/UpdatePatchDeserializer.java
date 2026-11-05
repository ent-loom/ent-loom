package com.entloom.crud.starter.web.patch;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** JSON 字段出现性直接绑定为类型化 Patch；显式 null 不丢失，未知字段不静默忽略。 */
public final class UpdatePatchDeserializer extends JsonDeserializer<UpdatePatch<?>> implements ContextualDeserializer {
    private final Supplier<EntityMetaRegistry> entities;
    private final Class<?> entityType;

    public UpdatePatchDeserializer(Supplier<EntityMetaRegistry> entities) {
        this(entities, null);
    }

    private UpdatePatchDeserializer(Supplier<EntityMetaRegistry> entities, Class<?> entityType) {
        this.entities = entities;
        this.entityType = entityType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
        throws JsonMappingException {
        JavaType type = property == null ? context.getContextualType() : property.getType();
        if (type == null || type.containedTypeCount() != 1 || type.containedType(0).getRawClass() == Object.class) {
            throw JsonMappingException.from(context, "UpdatePatch 必须声明明确的实体泛型");
        }
        return new UpdatePatchDeserializer(entities, type.containedType(0).getRawClass());
    }

    @Override
    public UpdatePatch<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        EntityMeta meta = entities.get().getEntityMeta(entityType);
        if (meta == null) throw JsonMappingException.from(parser, "UpdatePatch 实体未注册");
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) throw JsonMappingException.from(parser, "局部更新必须是 JSON 对象");
        Map<String, Object> values = new LinkedHashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (meta.resolveFieldMeta(field.getKey()) == null) {
                throw JsonMappingException.from(parser, "未知更新字段: " + field.getKey());
            }
            values.put(field.getKey(), parser.getCodec().treeToValue(field.getValue(), Object.class));
        }
        try {
            return new DefaultCommandPayloadBinder().bindUpdatePatch(values, entityType, meta);
        } catch (RuntimeException invalid) {
            throw JsonMappingException.from(parser, invalid.getMessage(), invalid);
        }
    }
}
