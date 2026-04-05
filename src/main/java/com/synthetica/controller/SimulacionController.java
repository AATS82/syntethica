package com.synthetica.controller;

import com.synthetica.dto.SimulacionRequestDTO;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.service.SimulacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulaciones")
public class SimulacionController {

    private final SimulacionService simulacionService;

    public SimulacionController(SimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @GetMapping("/encuesta/{encuestaId}")
    public List<Simulacion> listarPorEncuesta(@PathVariable Long encuestaId, Authentication auth) {
        return simulacionService.listarPorEncuesta(encuestaId, getUserId(auth));
    }

    @PostMapping
    public ResponseEntity<?> iniciar(@Valid @RequestBody SimulacionRequestDTO dto, Authentication auth) {
        try {
            Simulacion simulacion = simulacionService.iniciar(dto.getEncuestaId(), dto.getPersonaIds(), getUserId(auth));
            return ResponseEntity.ok(simulacion);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/progreso")
    public ResponseEntity<Map<String, Object>> progreso(@PathVariable Long id, Authentication auth) {
        simulacionService.verificarPropietario(id, getUserId(auth));
        Simulacion sim = simulacionService.obtenerPorId(id);
        return ResponseEntity.ok(Map.of(
            "id",                    sim.getId(),
            "estado",                sim.getEstado().name(),
            "porcentaje",            sim.getPorcentajeProgreso(),
            "totalRespuestas",       sim.getTotalRespuestas(),
            "respuestasCompletadas", sim.getRespuestasCompletadas()
        ));
    }

    @GetMapping("/{id}/resultados")
    public ResponseEntity<Map<Long, List<Respuesta>>> resultados(@PathVariable Long id, Authentication auth) {
        simulacionService.verificarPropietario(id, getUserId(auth));
        return ResponseEntity.ok(simulacionService.obtenerResultados(id));
    }

    private Long getUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return (Long) auth.getDetails();
    }
}
