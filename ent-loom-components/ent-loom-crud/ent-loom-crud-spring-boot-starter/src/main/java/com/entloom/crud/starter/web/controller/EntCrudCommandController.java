package com.entloom.crud.starter.web.controller;

import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.facade.EntCrudCommandFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"${entloom.crud.controller.base-path:/api/ent-crud}"})
@RequiredArgsConstructor
public class EntCrudCommandController {

    private final EntCrudCommandFacade entCrudCommandFacade;

    @PostMapping({"/{entity}/create", "/{entity}/create/{scene:.+}"})
    public CrudResponse<?> create(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.create(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/update", "/{entity}/update/{scene:.+}"})
    public CrudResponse<?> update(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.update(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/delete", "/{entity}/delete/{scene:.+}"})
    public CrudResponse<?> delete(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.delete(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/saveOrUpdate", "/{entity}/saveOrUpdate/{scene:.+}"})
    public CrudResponse<?> saveOrUpdate(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.saveOrUpdate(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/createBatch", "/{entity}/createBatch/{scene:.+}"})
    public CrudResponse<?> createBatch(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.createBatch(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/updateBatch", "/{entity}/updateBatch/{scene:.+}"})
    public CrudResponse<?> updateBatch(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.updateBatch(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/deleteBatch", "/{entity}/deleteBatch/{scene:.+}"})
    public CrudResponse<?> deleteBatch(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.deleteBatch(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/saveOrUpdateBatch", "/{entity}/saveOrUpdateBatch/{scene:.+}"})
    public CrudResponse<?> saveOrUpdateBatch(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.saveOrUpdateBatch(entity, scene, request, CrudInvocationContext.empty());
    }

    @PostMapping({"/{entity}/action", "/{entity}/action/{scene:.+}"})
    public CrudResponse<?> action(
        @PathVariable("entity") String entity,
        @PathVariable(value = "scene", required = false) String scene,
        @RequestBody(required = false) CrudCommandHttpRequest request
    ) {
        return entCrudCommandFacade.action(entity, scene, request, CrudInvocationContext.empty());
    }
}
