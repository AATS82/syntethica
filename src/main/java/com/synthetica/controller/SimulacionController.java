package com.synthetica.controller;

import com.synthetica.dto.SimulacionRequestDTO;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.service.SimulacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulaciones")
public class SimulacionController {

    private final SimulacionService simulacionService;
    
    public SimulacionController(SimulacionService simulacionService) {
    this.simulacionService = simulacionService;
}
    // Listar simulaciones de una encuesta
    @GetMapping("/encuesta/{encuestaId}")
    public List<Simulacion> listarPorEncuesta(@PathVariable Long encuestaId) {
        return simulacionService.listarPorEncuesta(encuestaId);
    }

    // Iniciar nueva simulación
    @PostMapping
    public ResponseEntity<?> iniciar(@RequestBody SimulacionRequestDTO dto) {
        try {
            Simulacion simulacion = simulacionService.iniciar(dto.getEncuestaId(), dto.getPersonaIds());
            return ResponseEntity.ok(simulacion);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Polling de progreso — Angular llama esto cada 2s
    @GetMapping("/{id}/progreso")
    public ResponseEntity<Map<String, Object>> progreso(@PathVariable Long id) {
        Simulacion sim = simulacionService.obtenerPorId(id);
        return ResponseEntity.ok(Map.of(
            "id",                    sim.getId(),
            "estado",                sim.getEstado().name(),
            "porcentaje",            sim.getPorcentajeProgreso(),
            "totalRespuestas",       sim.getTotalRespuestas(),
            "respuestasCompletadas", sim.getRespuestasCompletadas()
        ));
    }

    // Resultados completos agrupados por pregunta
    @GetMapping("/{id}/resultados")
    public ResponseEntity<Map<Long, List<Respuesta>>> resultados(@PathVariable Long id) {
        return ResponseEntity.ok(simulacionService.obtenerResultados(id));
    }
}
