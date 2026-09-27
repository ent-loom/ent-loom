package com.entloom.ddl.api;

import com.entloom.ddl.enums.GenerationStrategy;

/** DDL 主键生成默认值；仅补充未显式声明的主键字段。 */
public final class DdlGenerationDefaults {
    private final GenerationStrategy generationStrategy;

    public DdlGenerationDefaults() {
        this(GenerationStrategy.UNSET);
    }

    public DdlGenerationDefaults(GenerationStrategy generationStrategy) {
        this.generationStrategy = generationStrategy == null ? GenerationStrategy.UNSET : generationStrategy;
    }

    public GenerationStrategy generationStrategy() {
        return generationStrategy;
    }
}
