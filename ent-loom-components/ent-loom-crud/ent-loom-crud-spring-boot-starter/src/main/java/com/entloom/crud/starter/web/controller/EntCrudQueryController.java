package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.starter.web.dto.CrudReadHttpRequest;
import com.entloom.crud.starter.web.facade.EntCrudQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"${entloom.crud.controller.base-path:/api/ent-crud}"})
@RequiredArgsConstructor
public class EntCrudQueryController {

    private final EntCrudQueryFacade entCrudQueryFacade;

    @PostMapping({"/{entity}/page", "/{entity}/page/{scene:.+}"})
    public CrudResponse<?> page(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudReadHttpRequest request
    ) {
        return entCrudQueryFacade.page(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/list", "/{entity}/list/{scene:.+}"})
    public CrudResponse<?> list(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudReadHttpRequest request
    ) {
        return entCrudQueryFacade.list(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/findOne", "/{entity}/findOne/{scene:.+}"})
    public CrudResponse<?> findOne(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudReadHttpRequest request
    ) {
        return entCrudQueryFacade.findOne(entity, scene, request, CrudInvocationContext.empty());
    }

    /*
     * detail 用于必须存在的资源详情；findOne 用于允许不存在的唯一查询。
     * detail/{scene} 的路径段是场景名，不是 id。
     */
    @PostMapping({"/{entity}/detail", "/{entity}/detail/{scene:.+}"})
    public CrudResponse<?> detail(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudReadHttpRequest request
    ) {
        return entCrudQueryFacade.detail(entity, scene, request, CrudInvocationContext.empty());
    }
}
