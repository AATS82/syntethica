package com.synthetica.controller;

import com.synthetica.dto.EncuestaRapidaRequestDTO;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.service.AnalisisService;
import com.synthetica.service.EncuestaRapidaService;
import com.synthetica.service.SimulacionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @PostMapping
    public ResponseEntity<?> iniciar(@Valid @RequestBody EncuestaRapidaRequestDTO dto, Authentication auth) {
        log.info("[API] POST /api/encuesta-rapida — pregunta='{}'", dto.getPregunta());
        String email = (String) auth.getPrincipal();
        try {
            Simulacion sim = encuestaRapidaService.iniciar(dto, email);
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

    @GetMapping("/{id}/progreso")
    public ResponseEntity<Map<String, Object>> progreso(@PathVariable Long id, Authentication auth) {
        log.debug("[API] GET /api/encuesta-rapida/{}/progreso", id);
        simulacionService.verificarPropietario(id, getUserId(auth));
        Simulacion sim = simulacionService.obtenerPorId(id);
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", sim.getId());
        resp.put("estado", sim.getEstado().name());
        resp.put("porcentaje", sim.getPorcentajeProgreso());
        resp.put("totalRespuestas", sim.getTotalRespuestas());
        resp.put("respuestasCompletadas", sim.getRespuestasCompletadas());
        resp.put("loteActual", sim.getLoteActual());
        resp.put("totalLotes", sim.getTotalLotes());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/resultados")
    public ResponseEntity<List<Respuesta>> resultados(@PathVariable Long id, Authentication auth) {
        simulacionService.verificarPropietario(id, getUserId(auth));
        return ResponseEntity.ok(encuestaRapidaService.obtenerRespuestas(id));
    }

    @GetMapping("/{id}/analisis")
    public ResponseEntity<?> analisis(@PathVariable Long id, Authentication auth) {
        simulacionService.verificarPropietario(id, getUserId(auth));
        try {
            return ResponseEntity.ok(analisisService.analizar(id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/contrapregunta")
    public ResponseEntity<?> contrapregunta(@PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String pregunta = body.get("pregunta");
        if (pregunta == null || pregunta.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La pregunta no puede estar vacía"));
        }
        simulacionService.verificarPropietario(id, getUserId(auth));
        String email = (String) auth.getPrincipal();
        try {
            Simulacion sim = encuestaRapidaService.lanzarContrapregunta(id, pregunta.trim(), email);
            return ResponseEntity.ok(Map.of("simulacionId", sim.getId()));
        } catch (Exception e) {
            log.error("[API] Error al lanzar contrapregunta: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recientes")
    public ResponseEntity<List<Map<String, Object>>> recientes(Authentication auth) {
        List<Map<String, Object>> result = simulacionService.listarRecientes(getUserId(auth));
        return ResponseEntity.ok(result);
    }

    private Long getUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return (Long) auth.getDetails();
    }
}
