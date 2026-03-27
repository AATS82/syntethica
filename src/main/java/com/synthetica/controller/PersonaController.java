package com.synthetica.controller;

import com.synthetica.model.Persona;
import com.synthetica.service.PersonaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public Persona crear(@RequestBody Persona persona) {
        return personaService.crear(persona);
    }

    @PutMapping("/{id}")
    public Persona actualizar(@PathVariable Long id, @RequestBody Persona persona) {
        return personaService.actualizar(id, persona);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        personaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/personas/generar?pais=Chile
    @PostMapping("/generar")
    public ResponseEntity<?> generarAleatoria(@RequestParam(defaultValue = "Chile") String pais) {
        try {
            Persona persona = personaService.generarAleatoria(pais);
            return ResponseEntity.ok(persona);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al generar persona: " + e.getMessage()));
        }
    }
}
