package com.synthetica.controller;

import com.synthetica.dto.EncuestaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.service.EncuestaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/encuestas")
public class EncuestaController {

    private final EncuestaService encuestaService;

    public EncuestaController(EncuestaService encuestaService) {
        this.encuestaService = encuestaService;
    }

    @GetMapping
    public List<Encuesta> listar() {
        return encuestaService.listarTodas();
    }

    @GetMapping("/{id}")
    public Encuesta obtener(@PathVariable Long id) {
        return encuestaService.obtenerPorId(id);
    }

    @PostMapping
    public Encuesta crear(@Valid @RequestBody EncuestaRequestDTO dto) {
        return encuestaService.crear(dto);
    }

    @PutMapping("/{id}")
    public Encuesta actualizar(@PathVariable Long id, @Valid @RequestBody EncuestaRequestDTO dto) {
        return encuestaService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        encuestaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
