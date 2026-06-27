package com.entloom.doc.core.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business-side DOC entity override.
 */
public final class DocEntityOverride {
    private final String entityName;
    private final String description;
    private final String group;
    private final String remark;
    private final Boolean hidden;
    private final List<String> visibleFor;
    private final Map<String, DocFieldOverride> fields;

    private DocEntityOverride(Builder builder) {
        this.entityName = builder.entityName;
        this.description = builder.description;
        this.group = builder.group;
        this.remark = builder.remark;
        this.hidden = builder.hidden;
        this.visibleFor = immutable(builder.visibleFor);
        this.fields = immutableFields(builder.fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String entityName() {
        return entityName;
    }

    public String description() {
        return description;
    }

    public String group() {
        return group;
    }

    public String remark() {
        return remark;
    }

    public Boolean hidden() {
        return hidden;
    }

    public List<String> visibleFor() {
        return visibleFor;
    }

    public Map<String, DocFieldOverride> fields() {
        return fields;
    }

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty()
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static Map<String, DocFieldOverride> immutableFields(Map<String, DocFieldOverride> values) {
        return values == null || values.isEmpty()
            ? Collections.<String, DocFieldOverride>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, DocFieldOverride>(values));
    }

    public static final class Builder {
        private String entityName;
        private String description;
        private String group;
        private String remark;
        private Boolean hidden;
        private List<String> visibleFor;
        private final Map<String, DocFieldOverride> fields = new LinkedHashMap<String, DocFieldOverride>();

        private Builder() {
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder remark(String remark) {
            this.remark = remark;
            return this;
        }

        public Builder hidden(Boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder visibleFor(List<String> visibleFor) {
            this.visibleFor = visibleFor;
            return this;
        }

        public Builder field(DocFieldOverride field) {
            if (field != null && field.property() != null && !field.property().trim().isEmpty()) {
                fields.put(field.property(), field);
            }
            return this;
        }

        public DocEntityOverride build() {
            return new DocEntityOverride(this);
        }
    }
}
