package com.entloom.meta.contract.diagnostic;

import com.entloom.meta.contract.value.MetaValueSource;
import java.util.Objects;

/**
 * 结构化 Meta 诊断。
 */
public final class MetaDiagnostic {
    private final MetaDiagnosticLevel level;
    private final MetaDiagnosticCode code;
    private final String entity;
    private final String entityClass;
    private final String field;
    private final MetaValueSource source;
    private final String property;
    private final String location;
    private final String message;

    private MetaDiagnostic(Builder builder) {
        this.level = builder.level == null ? MetaDiagnosticLevel.INFO : builder.level;
        this.code = builder.code == null ? MetaDiagnosticCode.UNKNOWN : builder.code;
        this.entity = trimToNull(builder.entity);
        this.entityClass = trimToNull(builder.entityClass);
        this.field = trimToNull(builder.field);
        this.source = builder.source;
        this.property = trimToNull(builder.property);
        this.location = trimToNull(builder.location);
        this.message = trimToNull(builder.message);
    }

    public static Builder error(MetaDiagnosticCode code) {
        return builder(MetaDiagnosticLevel.ERROR, code);
    }

    public static Builder warn(MetaDiagnosticCode code) {
        return builder(MetaDiagnosticLevel.WARN, code);
    }

    public static Builder info(MetaDiagnosticCode code) {
        return builder(MetaDiagnosticLevel.INFO, code);
    }

    public static Builder builder(MetaDiagnosticLevel level, MetaDiagnosticCode code) {
        return new Builder().level(level).code(code);
    }

    public MetaDiagnosticLevel level() {
        return level;
    }

    public MetaDiagnosticCode code() {
        return code;
    }

    public String entity() {
        return entity;
    }

    public String entityClass() {
        return entityClass;
    }

    public String field() {
        return field;
    }

    public MetaValueSource source() {
        return source;
    }

    public String property() {
        return property;
    }

    public String location() {
        return location;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetaDiagnostic)) {
            return false;
        }
        MetaDiagnostic that = (MetaDiagnostic) other;
        return level == that.level
            && code == that.code
            && Objects.equals(entity, that.entity)
            && Objects.equals(entityClass, that.entityClass)
            && Objects.equals(field, that.field)
            && source == that.source
            && Objects.equals(property, that.property)
            && Objects.equals(location, that.location)
            && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, code, entity, entityClass, field, source, property, location, message);
    }

    @Override
    public String toString() {
        return "MetaDiagnostic{"
            + "level=" + level
            + ", code=" + code
            + ", entity='" + entity + '\''
            + ", entityClass='" + entityClass + '\''
            + ", field='" + field + '\''
            + ", source=" + source
            + ", property='" + property + '\''
            + ", location='" + location + '\''
            + ", message='" + message + '\''
            + '}';
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class Builder {
        private MetaDiagnosticLevel level;
        private MetaDiagnosticCode code;
        private String entity;
        private String entityClass;
        private String field;
        private MetaValueSource source;
        private String property;
        private String location;
        private String message;

        public Builder level(MetaDiagnosticLevel level) {
            this.level = level;
            return this;
        }

        public Builder code(MetaDiagnosticCode code) {
            this.code = code;
            return this;
        }

        public Builder entity(String entity) {
            this.entity = entity;
            return this;
        }

        public Builder entityClass(Class<?> entityClass) {
            this.entityClass = entityClass == null ? null : entityClass.getName();
            return this;
        }

        public Builder entityClass(String entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder source(MetaValueSource source) {
            this.source = source;
            return this;
        }

        public Builder property(String property) {
            this.property = property;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public MetaDiagnostic build() {
            return new MetaDiagnostic(this);
        }
    }
}
