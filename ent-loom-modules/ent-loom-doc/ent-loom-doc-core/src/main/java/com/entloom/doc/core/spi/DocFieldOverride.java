package com.entloom.doc.core.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Business-side DOC field override.
 */
public final class DocFieldOverride {
    private final String property;
    private final String name;
    private final String description;
    private final String example;
    private final List<String> examples;
    private final String group;
    private final String remark;
    private final Boolean hidden;
    private final List<String> visibleFor;

    private DocFieldOverride(Builder builder) {
        this.property = builder.property;
        this.name = builder.name;
        this.description = builder.description;
        this.example = builder.example;
        this.examples = immutable(builder.examples);
        this.group = builder.group;
        this.remark = builder.remark;
        this.hidden = builder.hidden;
        this.visibleFor = immutable(builder.visibleFor);
    }

    public static Builder builder(String property) {
        return new Builder(property);
    }

    public String property() {
        return property;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String example() {
        return example;
    }

    public List<String> examples() {
        return examples;
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

    private static List<String> immutable(List<String> values) {
        return values == null || values.isEmpty()
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(values));
    }

    public static final class Builder {
        private final String property;
        private String name;
        private String description;
        private String example;
        private List<String> examples;
        private String group;
        private String remark;
        private Boolean hidden;
        private List<String> visibleFor;

        private Builder(String property) {
            this.property = property;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder example(String example) {
            this.example = example;
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples;
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

        public DocFieldOverride build() {
            return new DocFieldOverride(this);
        }
    }
}
