package com.entloom.crud.starter.web.dto;

import com.entloom.crud.api.enums.CrudNullFieldMode;
import com.entloom.crud.core.exception.CrudException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudReadOptionsTest {
    @Test
    void null_field_mode_should_support_include_and_omit() {
        CrudReadOptions options = new CrudReadOptions();

        options.setRawNullFieldMode("include");
        Assertions.assertEquals(CrudNullFieldMode.INCLUDE, options.resolveNullFieldMode());

        options.setRawNullFieldMode("OMIT");
        Assertions.assertEquals(CrudNullFieldMode.OMIT, options.resolveNullFieldMode());
    }

    @Test
    void null_field_mode_should_reject_unsupported_value() {
        CrudReadOptions options = new CrudReadOptions();
        options.setRawNullFieldMode("DEFAULT");

        CrudException exception = Assertions.assertThrows(CrudException.class, options::resolveNullFieldMode);
        Assertions.assertTrue(exception.getMessage().contains("仅支持 INCLUDE 或 OMIT"));
    }
}
