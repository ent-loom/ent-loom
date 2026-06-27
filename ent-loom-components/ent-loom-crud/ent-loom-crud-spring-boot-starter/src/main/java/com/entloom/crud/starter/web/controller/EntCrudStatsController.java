package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.facade.EntCrudStatsFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计 HTTP 入口。
 */
@RestController
@RequestMapping({"${entloom.crud.controller.base-path:/api/ent-crud}"})
@RequiredArgsConstructor
public class EntCrudStatsController {
    private final EntCrudStatsFacade entCrudStatsFacade;

    @PostMapping({"/{entity}/stats", "/{entity}/stats/{scene:.+}"})
    public CrudResponse<?> stats(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudStatsHttpRequest request
    ) {
        return entCrudStatsFacade.stats(entity, scene, request, CrudInvocationContext.empty());
    }
}
