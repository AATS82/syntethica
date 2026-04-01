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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    private static final Logger log = LoggerFactory.getLogger(EncuestaRapidaController.class);

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
    public ResponseEntity<?> iniciar(@RequestBody EncuestaRapidaRequestDTO dto, Authentication auth) {
        log.info("[API] POST /api/encuesta-rapida — pregunta='{}'", dto.getPregunta());
        boolean autenticado = auth != null && auth.isAuthenticated();
        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 50;
        if (!autenticado && cantidad > 50) {
            return ResponseEntity.status(401).body(Map.of("error", "Debes iniciar sesión para encuestar a más de 50 personas"));
        }
        Long usuarioId = autenticado ? (Long) auth.getDetails() : null;
        try {
            Simulacion sim = encuestaRapidaService.iniciar(dto, usuarioId);
            log.info("[API] Encuesta rápida iniciada — simulacionId={}", sim.getId());
            return ResponseEntity.ok(Map.of(
                    "simulacionId", sim.getId(),
                    "total", sim.getTotalRespuestas(),
                    "estado", sim.getEstado().name()
            ));
        } catch (Exception e) {
            log.error("[API] Error al iniciar encuesta rápida: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Polling de progreso
    @GetMapping("/{id}/progreso")
    public ResponseEntity<Map<String, Object>> progreso(@PathVariable Long id) {
        log.debug("[API] GET /api/encuesta-rapida/{}/progreso", id);
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

    // Lanzar contrapregunta sobre los mismos perfiles de una simulación anterior
    @PostMapping("/{id}/contrapregunta")
    public ResponseEntity<?> contrapregunta(@PathVariable Long id,
            @RequestBody Map<String, String> body, Authentication auth) {
        log.info("[API] POST /api/encuesta-rapida/{}/contrapregunta", id);
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String pregunta = body.get("pregunta");
        if (pregunta == null || pregunta.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'pregunta' es requerido"));
        }
        Long usuarioId = (Long) auth.getDetails();
        try {
            Simulacion sim = encuestaRapidaService.iniciarContrapregunta(id, pregunta, usuarioId);
            return ResponseEntity.ok(Map.of(
                    "simulacionId", sim.getId(),
                    "simulacionOrigenId", id,
                    "total", sim.getTotalRespuestas(),
                    "estado", sim.getEstado().name()
            ));
        } catch (Exception e) {
            log.error("[API] Error al iniciar contrapregunta: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recientes")
    public ResponseEntity<List<Map<String, Object>>> recientes(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Long usuarioId = (Long) auth.getDetails();
        List<Map<String, Object>> result = simulacionService.listarRecientesPorUsuario(usuarioId);
        return ResponseEntity.ok(result);
    }
}
