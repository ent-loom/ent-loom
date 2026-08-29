package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudExportData;
import com.entloom.crud.api.model.CrudExportHttpRequest;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.CrudTaskData;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.starter.web.support.CrudWebInvocationContext;
import com.entloom.crud.starter.web.facade.EntCrudExportFacade;
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
 * 导出 HTTP 入口。
 */
@RestController
@RequestMapping({"${entloom.crud.controller.base-path:/api/ent-crud}"})
@RequiredArgsConstructor
public class EntCrudExportController {
    private final EntCrudExportFacade exportFacade;

    @PostMapping({"/{entity}/export/preview", "/{entity}/export/{scene:.+}/preview"})
    public CrudResponse<CrudExportData> preview(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudExportHttpRequest request
    ) {
        return exportFacade.preview(entity, scene, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/export/submit", "/{entity}/export/{scene:.+}/submit"})
    public CrudResponse<CrudExportData> submit(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudExportHttpRequest request
    ) {
        return exportFacade.submit(entity, scene, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/export/status", "/{entity}/export/tasks/{taskId}/status"})
    public CrudResponse<CrudTaskData> status(
        @PathVariable("entity") String entity,
        @PathVariable(value = "taskId", required = false) String taskId,
        @RequestBody(required = false) CrudExportHttpRequest request
    ) {
        return exportFacade.status(entity, null, taskId, request, CrudWebInvocationContext.http());
    }

    @PostMapping({"/{entity}/export/download", "/{entity}/export/tasks/{taskId}/download"})
    public ResponseEntity<StreamingResponseBody> download(
        @PathVariable("entity") String entity,
        @PathVariable(value = "taskId", required = false) String taskId,
        @RequestBody(required = false) CrudExportHttpRequest request
    ) {
        return toDownloadResponse(exportFacade.download(entity, null, taskId, request, CrudWebInvocationContext.http()));
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
        String safeName = fileName == null ? "export" : fileName.replace("\r", "").replace("\n", "");
        try {
            return URLEncoder.encode(safeName, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return "export";
        }
    }
}
