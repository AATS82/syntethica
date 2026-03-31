package com.synthetica.controller;

import com.synthetica.dto.EncuestaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.service.EncuestaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/encuestas")
public class EncuestaController {

    private final EncuestaService encuestaService;

    public EncuestaController(EncuestaService encuestaService) {
        this.encuestaService = encuestaService;
    }

    @GetMapping
    public List<Encuesta> listar(Authentication auth) {
        return encuestaService.listarPorUsuario(getUserId(auth));
    }

    @GetMapping("/{id}")
    public Encuesta obtener(@PathVariable Long id, Authentication auth) {
        Encuesta encuesta = encuestaService.obtenerPorId(id);
        if (encuesta.getUsuario() == null || !encuesta.getUsuario().getId().equals(getUserId(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta encuesta");
        }
        return encuesta;
    }

    @PostMapping
    public Encuesta crear(@Valid @RequestBody EncuestaRequestDTO dto, Authentication auth) {
        return encuestaService.crear(dto, getUserId(auth));
    }

    @PutMapping("/{id}")
    public Encuesta actualizar(@PathVariable Long id,
                               @Valid @RequestBody EncuestaRequestDTO dto,
                               Authentication auth) {
        return encuestaService.actualizar(id, dto, getUserId(auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        encuestaService.eliminar(id, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return (Long) auth.getDetails();
    }
}
