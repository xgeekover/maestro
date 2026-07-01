package io.maestro.backend.api;

import io.maestro.backend.module.ModuleService;
import io.maestro.backend.support.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    private final ModuleService modules;

    public ModuleController(ModuleService modules) {
        this.modules = modules;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ModuleResponse create(@Valid @RequestBody Dtos.CreateModuleRequest req) {
        return Dtos.ModuleResponse.of(
                modules.create(req.name(), req.version(), req.specJson(), req.source()));
    }

    @GetMapping
    public List<Dtos.ModuleResponse> list() {
        return modules.list().stream().map(Dtos.ModuleResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dtos.ModuleResponse> get(@PathVariable String id) {
        return modules.get(id)
                .map(Dtos.ModuleResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Dtos.ModuleResponse update(@PathVariable String id,
                                      @Valid @RequestBody Dtos.CreateModuleRequest req) {
        return modules.update(id, req.name(), req.version(), req.specJson(), req.source())
                .map(Dtos.ModuleResponse::of)
                .orElseThrow(() -> new NotFoundException("모듈 없음: " + id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!modules.delete(id)) {
            throw new NotFoundException("모듈 없음: " + id);
        }
    }
}
