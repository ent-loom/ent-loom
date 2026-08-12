package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudExportHttpRequest;
import com.entloom.crud.api.model.CrudImportHttpRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class ImportExportControllerContractTest {
    @Test
    void export_controller_should_keep_documented_status_and_download_paths() throws Exception {
        assertPostMapping(
            EntCrudExportController.class.getMethod("status", String.class, String.class, CrudExportHttpRequest.class),
            "/{entity}/export/status"
        );
        assertPostMapping(
            EntCrudExportController.class.getMethod("download", String.class, String.class, CrudExportHttpRequest.class),
            "/{entity}/export/download"
        );
    }

    @Test
    void import_controller_should_keep_documented_status_and_error_download_paths() throws Exception {
        assertPostMapping(
            EntCrudImportController.class.getMethod("status", String.class, String.class, CrudImportHttpRequest.class),
            "/{entity}/import/status"
        );
        assertPostMapping(
            EntCrudImportController.class.getMethod("downloadError", String.class, String.class, CrudImportHttpRequest.class),
            "/{entity}/import/error"
        );
    }

    private void assertPostMapping(Method method, String expectedPath) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        Assertions.assertNotNull(mapping);
        Assertions.assertTrue(Arrays.asList(mapping.value()).contains(expectedPath));
    }
}
