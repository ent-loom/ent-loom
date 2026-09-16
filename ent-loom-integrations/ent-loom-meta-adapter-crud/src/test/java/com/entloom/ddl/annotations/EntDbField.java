package com.entloom.ddl.annotations;

import com.entloom.ddl.enums.GenerationStrategy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntDbField {
    GenerationStrategy generationStrategy() default GenerationStrategy.UNSET;
}
