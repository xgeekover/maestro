package io.maestro.backend.api;

import io.maestro.backend.domain.ScriptService;
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
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scripts;

    public ScriptController(ScriptService scripts) {
        this.scripts = scripts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ScriptResponse create(@Valid @RequestBody Dtos.CreateScriptRequest req) {
        return Dtos.ScriptResponse.of(scripts.create(req.name(), req.source()));
    }

    @GetMapping
    public List<Dtos.ScriptResponse> list() {
        return scripts.list().stream().map(Dtos.ScriptResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dtos.ScriptResponse> get(@PathVariable String id) {
        return scripts.get(id)
                .map(Dtos.ScriptResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Dtos.ScriptResponse> update(@PathVariable String id,
                                                      @Valid @RequestBody Dtos.CreateScriptRequest req) {
        return scripts.update(id, req.name(), req.source())
                .map(Dtos.ScriptResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        scripts.delete(id);
    }
}
