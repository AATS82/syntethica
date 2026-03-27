/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.synthetica.controller;

import com.synthetica.dto.EncuestaRapidaRequestDTO;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.service.AnalisisService;
import com.synthetica.service.EncuestaRapidaService;
import com.synthetica.service.SimulacionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Totoralillo
 */
@RestController
@RequestMapping("/api/encuesta-rapida")
public class EncuestaRapidaController {

    private final EncuestaRapidaService encuestaRapidaService;
    private final SimulacionService simulacionService;
    private final AnalisisService analisisService;

    public EncuestaRapidaController(EncuestaRapidaService encuestaRapidaService,
            SimulacionService simulacionService,
            AnalisisService analisisService) {
        this.encuestaRapidaService = encuestaRapidaService;
        this.simulacionService = simulacionService;
        this.analisisService = analisisService;
    }

    // Lanzar encuesta rápida
    @PostMapping
    public ResponseEntity<?> iniciar(@RequestBody EncuestaRapidaRequestDTO dto) {
        try {
            Simulacion sim = encuestaRapidaService.iniciar(dto);
            return ResponseEntity.ok(Map.of(
                    "simulacionId", sim.getId(),
                    "total", sim.getTotalRespuestas(),
                    "estado", sim.getEstado().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Polling de progreso
    @GetMapping("/{id}/progreso")
    public ResponseEntity<Map<String, Object>> progreso(@PathVariable Long id) {
        Simulacion sim = simulacionService.obtenerPorId(id);
        return ResponseEntity.ok(Map.of(
                "id", sim.getId(),
                "estado", sim.getEstado().name(),
                "porcentaje", sim.getPorcentajeProgreso(),
                "totalRespuestas", sim.getTotalRespuestas(),
                "respuestasCompletadas", sim.getRespuestasCompletadas()
        ));
    }

    // Obtener respuestas completas
    @GetMapping("/{id}/resultados")
    public ResponseEntity<List<Respuesta>> resultados(@PathVariable Long id) {
        return ResponseEntity.ok(encuestaRapidaService.obtenerRespuestas(id));
    }

    @GetMapping("/{id}/analisis")
    public ResponseEntity<?> analisis(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(analisisService.analizar(id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
