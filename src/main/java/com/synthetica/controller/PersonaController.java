package com.synthetica.controller;

import com.synthetica.model.Persona;
import com.synthetica.service.PersonaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping
    public List<Persona> listar() {
        return personaService.listarTodas();
    }

    @GetMapping("/{id}")
    public Persona obtener(@PathVariable Long id) {
        return personaService.obtenerPorId(id);
    }

    @PostMapping
    public Persona crear(@RequestBody Persona persona, Authentication auth) {
        requireAuth(auth);
        return personaService.crear(persona);
    }

    @PutMapping("/{id}")
    public Persona actualizar(@PathVariable Long id, @RequestBody Persona persona, Authentication auth) {
        requireAuth(auth);
        return personaService.actualizar(id, persona);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        requireAuth(auth);
        personaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generar")
    public ResponseEntity<?> generarAleatoria(@RequestParam(defaultValue = "Chile") String pais,
                                              Authentication auth) {
        requireAuth(auth);
        try {
            Persona persona = personaService.generarAleatoria(pais);
            return ResponseEntity.ok(persona);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al generar persona: " + e.getMessage()));
        }
    }

    private void requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
    }
}
