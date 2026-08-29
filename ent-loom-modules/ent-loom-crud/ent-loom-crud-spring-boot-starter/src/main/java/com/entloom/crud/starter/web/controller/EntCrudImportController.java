package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudImportData;
import com.entloom.crud.api.model.CrudImportHttpRequest;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.CrudTaskData;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.starter.web.support.CrudWebInvocationContext;
import com.entloom.crud.starter.web.facade.EntCrudImportFacade;
import com.entloom.crud.starter.web.facade.FileDownload;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 导入 HTTP 入口。
 */
@RestController
@RequestMapping({"${entloom.crud.controller.base-path:/api/ent-crud}"})
@RequiredArgsConstructor
public class EntCrudImportController {
    private final EntCrudImportFacade importFacade;

    @PostMapping({"/{entity}/import/validate", "/{entity}/import/{scene:.+}/validate"})
    public CrudResponse<CrudImportData> validate(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudImportHttpRequest request
    ) {
        return importFacade.validate(entity, scene, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/import/submit", "/{entity}/import/{scene:.+}/submit"})
    public CrudResponse<CrudImportData> submit(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudImportHttpRequest request
    ) {
        return importFacade.submit(entity, scene, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/import/status", "/{entity}/import/tasks/{taskId}/status"})
    public CrudResponse<CrudTaskData> status(
        @PathVariable("entity") String entity,
        @PathVariable(value = "taskId", required = false) String taskId,
        @RequestBody(required = false) CrudImportHttpRequest request
    ) {
        return importFacade.status(entity, null, taskId, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/import/error", "/{entity}/import/tasks/{taskId}/errors/download"})
    public ResponseEntity<StreamingResponseBody> downloadError(
        @PathVariable("entity") String entity,
        @PathVariable(value = "taskId", required = false) String taskId,
        @RequestBody(required = false) CrudImportHttpRequest request
    ) {
        return toDownloadResponse(importFacade.downloadError(entity, null, taskId, request, CrudWebInvocationContext.http()));
    }

    private ResponseEntity<StreamingResponseBody> toDownloadResponse(FileDownload download) {
        FileRef file = download.getFile();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        if (file.getSize() != null && file.getSize().longValue() >= 0) {
            headers.setContentLength(file.getSize().longValue());
        }
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFileName(file.getFileName()));
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = download.openStream()) {
                StreamUtils.copy(inputStream, outputStream);
            }
        };
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private String encodeFileName(String fileName) {
        String safeName = fileName == null ? "import-errors" : fileName.replace("\r", "").replace("\n", "");
        try {
            return URLEncoder.encode(safeName, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return "import-errors";
        }
    }
}
