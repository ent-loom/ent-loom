package com.entloom.crud.starter.web.error;

import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudExportController;
import com.entloom.crud.starter.web.controller.EntCrudImportController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.controller.EntCrudStatsController;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class CrudHttpExceptionTranslatorContractTest {
    @Test
    void advice_should_cover_all_crud_http_controllers() {
        RestControllerAdvice advice = CrudHttpExceptionTranslator.class.getAnnotation(RestControllerAdvice.class);
        Assertions.assertNotNull(advice);

        List<Class<?>> controllerTypes = Arrays.asList(advice.assignableTypes());
        Assertions.assertTrue(controllerTypes.contains(EntCrudQueryController.class));
        Assertions.assertTrue(controllerTypes.contains(EntCrudCommandController.class));
        Assertions.assertTrue(controllerTypes.contains(EntCrudStatsController.class));
        Assertions.assertTrue(controllerTypes.contains(EntCrudImportController.class));
        Assertions.assertTrue(controllerTypes.contains(EntCrudExportController.class));
    }
}
