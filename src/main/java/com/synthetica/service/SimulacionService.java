package com.synthetica.service;

import com.synthetica.model.*;
import com.synthetica.model.enums.EstadoSimulacion;
import com.synthetica.model.enums.TipoPregunta;
import com.synthetica.repository.RespuestaRepository;
import com.synthetica.repository.SimulacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SimulacionService {

    private static final Logger log = LoggerFactory.getLogger(SimulacionService.class);

    private final SimulacionRepository simulacionRepository;
    private final RespuestaRepository  respuestaRepository;
    private final EncuestaService      encuestaService;
    private final PersonaService       personaService;
    private final ClaudeService        claudeService;
        
    public SimulacionService(SimulacionRepository simulacionRepository, RespuestaRepository respuestaRepository,
                         EncuestaService encuestaService, PersonaService personaService, ClaudeService claudeService) {
    this.simulacionRepository = simulacionRepository;
    this.respuestaRepository = respuestaRepository;
    this.encuestaService = encuestaService;
    this.personaService = personaService;
    this.claudeService = claudeService;
}
    
    // ── Crear simulación y lanzarla de forma asíncrona ────────────────────────
    @Transactional
    public Simulacion iniciar(Long encuestaId, List<Long> personaIds) {
        Encuesta encuesta = encuestaService.obtenerPorId(encuestaId);
        List<Persona> personas = personaService.obtenerPorIds(personaIds);

        if (personas.isEmpty()) throw new RuntimeException("No se encontraron personas con los IDs proporcionados");
        if (encuesta.getPreguntas().isEmpty()) throw new RuntimeException("La encuesta no tiene preguntas");

        Simulacion simulacion = new Simulacion();
        simulacion.setEncuesta(encuesta);
        simulacion.setEstado(EstadoSimulacion.PENDIENTE);
        simulacion.setTotalRespuestas(personas.size() * encuesta.getPreguntas().size());
        simulacion.setRespuestasCompletadas(0);
        Simulacion saved = simulacionRepository.save(simulacion);

        // Lanzar proceso asíncrono
        ejecutarAsync(saved.getId(), personas, encuesta);

        return saved;
    }

    // ── Proceso asíncrono: llama a Claude para cada persona × pregunta ─────────
    @Async("simulacionExecutor")
    public void ejecutarAsync(Long simulacionId, List<Persona> personas, Encuesta encuesta) {
        Simulacion simulacion = simulacionRepository.findById(simulacionId).orElseThrow();
        simulacion.setEstado(EstadoSimulacion.CORRIENDO);
        simulacionRepository.save(simulacion);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Persona persona : personas) {
            for (Pregunta pregunta : encuesta.getPreguntas()) {

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String respuestaTexto = claudeService.responderPregunta(
                            persona, pregunta, encuesta.getContexto()
                        );

                        Respuesta respuesta = new Respuesta();
                        respuesta.setSimulacion(simulacion);
                        respuesta.setPersona(persona);
                        respuesta.setPregunta(pregunta);

                        // Parsear Likert si corresponde
                        if (pregunta.getTipo() == TipoPregunta.LIKERT) {
                            Integer valor = parsearValorLikert(respuestaTexto);
                            respuesta.setValorLikert(valor);
                            respuesta.setRespuestaTexto(respuestaTexto);
                        } else {
                            respuesta.setRespuestaTexto(respuestaTexto);
                        }

                        guardarRespuestaYAvanzar(simulacionId, respuesta);

                    } catch (Exception e) {
                        log.error("Error al obtener respuesta de Claude para persona {} pregunta {}: {}",
                            persona.getId(), pregunta.getId(), e.getMessage());
                        // Guardar respuesta de error para no bloquear el progreso
                        Respuesta respuestaError = new Respuesta();
                        respuestaError.setSimulacion(simulacion);
                        respuestaError.setPersona(persona);
                        respuestaError.setPregunta(pregunta);
                        respuestaError.setRespuestaTexto("[Error al obtener respuesta]");
                        guardarRespuestaYAvanzar(simulacionId, respuestaError);
                    }
                });

                futures.add(future);
            }
        }

        // Esperar que todas las llamadas terminen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Marcar como completa
        Simulacion finalizada = simulacionRepository.findById(simulacionId).orElseThrow();
        finalizada.setEstado(EstadoSimulacion.COMPLETA);
        finalizada.setFinalizadoEn(LocalDateTime.now());
        simulacionRepository.save(finalizada);

        log.info("Simulacion {} completada. Total respuestas: {}", simulacionId, finalizada.getRespuestasCompletadas());
    }

    // ── Guardar respuesta e incrementar contador (sincronizado) ───────────────
    @Transactional
    synchronized void guardarRespuestaYAvanzar(Long simulacionId, Respuesta respuesta) {
        respuestaRepository.save(respuesta);
        Simulacion sim = simulacionRepository.findById(simulacionId).orElseThrow();
        sim.setRespuestasCompletadas(sim.getRespuestasCompletadas() + 1);
        simulacionRepository.save(sim);
    }

    // ── Consultar progreso ─────────────────────────────────────────────────────
    public Simulacion obtenerPorId(Long id) {
        return simulacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Simulación no encontrada: " + id));
    }

    public List<Simulacion> listarPorEncuesta(Long encuestaId) {
        return simulacionRepository.findByEncuestaIdOrderByCreadoEnDesc(encuestaId);
    }

    // ── Obtener resultados agrupados por pregunta ──────────────────────────────
    public Map<Long, List<Respuesta>> obtenerResultados(Long simulacionId) {
        List<Respuesta> todas = respuestaRepository.findBySimulacionId(simulacionId);
        return todas.stream().collect(Collectors.groupingBy(r -> r.getPregunta().getId()));
    }

    // ── Helper: extraer número 1-5 de la respuesta Likert ─────────────────────
    private Integer parsearValorLikert(String texto) {
        if (texto == null || texto.isBlank()) return null;
        // Busca el primer dígito entre 1 y 5 al inicio del texto
        for (char c : texto.toCharArray()) {
            if (c >= '1' && c <= '5') return c - '0';
        }
        return null;
    }
}
