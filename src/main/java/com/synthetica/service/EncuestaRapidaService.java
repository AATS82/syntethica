package com.synthetica.service;

import com.synthetica.dto.EncuestaRapidaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.model.Persona;
import com.synthetica.model.Pregunta;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.model.enums.EstadoSimulacion;
import com.synthetica.model.enums.TipoPregunta;
import com.synthetica.repository.EncuestaRepository;
import com.synthetica.repository.PersonaRepository;
import com.synthetica.repository.RespuestaRepository;
import com.synthetica.repository.SimulacionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncuestaRapidaService {

    private static final Logger log = LoggerFactory.getLogger(EncuestaRapidaService.class);

    private final PersonaRepository personaRepository;
    private final EncuestaRepository encuestaRepository;
    private final SimulacionRepository simulacionRepository;
    private final RespuestaRepository respuestaRepository;
    private final ClaudeService claudeService;

    public EncuestaRapidaService(PersonaRepository personaRepository,
            EncuestaRepository encuestaRepository,
            SimulacionRepository simulacionRepository,
            RespuestaRepository respuestaRepository,
            ClaudeService claudeService) {
        this.personaRepository = personaRepository;
        this.encuestaRepository = encuestaRepository;
        this.simulacionRepository = simulacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.claudeService = claudeService;
    }

    // Sin @Transactional aquí
    public Simulacion iniciar(EncuestaRapidaRequestDTO dto) {
        log.info("[ENCUESTA-RAPIDA] Iniciando encuesta rápida. Pregunta: '{}'", dto.getPregunta());

        var filtros = dto.getFiltros();
        String pais = null, sexo = null, educacion = null, nse = null;
        Integer edadMin = null, edadMax = null;

        if (filtros != null) {
            pais = filtros.getPais();
            sexo = filtros.getSexoFiltro();
            edadMin = filtros.getEdadMin();
            edadMax = filtros.getEdadMax();
            educacion = filtros.getEducacion();
            nse = filtros.getNivelSocioeconomico();
            log.info("[ENCUESTA-RAPIDA] Filtros aplicados — pais={}, sexo={}, edad={}-{}, educacion={}, nse={}",
                    pais, sexo, edadMin, edadMax, educacion, nse);
        } else {
            log.info("[ENCUESTA-RAPIDA] Sin filtros, se usarán personas aleatorias");
        }

        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 50;
        log.info("[ENCUESTA-RAPIDA] Cantidad solicitada: {}", cantidad);

        List<Persona> personas = personaRepository.findByFiltros(
                pais, sexo, edadMin, edadMax, educacion, nse, cantidad
        );

        if (personas.isEmpty()) {
            log.warn("[ENCUESTA-RAPIDA] No se encontraron personas con los filtros indicados");
            throw new RuntimeException("No hay personas que coincidan con los filtros seleccionados");
        }
        log.info("[ENCUESTA-RAPIDA] Se encontraron {} personas para la simulación", personas.size());

        // Guardar encuesta y pregunta en transacción propia — commit inmediato
        log.debug("[ENCUESTA-RAPIDA] Guardando encuesta y pregunta en BD...");
        Long[] ids = guardarEncuestaYPregunta(dto.getPregunta());
        Long encuestaId = ids[0];
        Long preguntaId = ids[1];
        log.info("[ENCUESTA-RAPIDA] Encuesta guardada — encuestaId={}, preguntaId={}", encuestaId, preguntaId);

        // Guardar simulación en transacción propia — commit inmediato
        List<Long> personaIds = personas.stream().map(Persona::getId).toList();
        log.debug("[ENCUESTA-RAPIDA] Guardando simulación para encuestaId={}...", encuestaId);
        Simulacion simulacion = guardarSimulacion(encuestaId, personaIds.size());
        log.info("[ENCUESTA-RAPIDA] Simulación creada — simulacionId={}, estado={}, total={}",
                simulacion.getId(), simulacion.getEstado(), simulacion.getTotalRespuestas());

        // Ahora sí lanzar async — todo está commiteado en BD
        log.info("[ENCUESTA-RAPIDA] Lanzando ejecución asíncrona para simulacionId={}", simulacion.getId());
        ejecutarAsync(simulacion.getId(), personaIds, preguntaId);

        return simulacion;
    }

    @Transactional
    public Long[] guardarEncuestaYPregunta(String textoPregunta) {
        log.debug("[ENCUESTA-RAPIDA] Creando entidades Encuesta y Pregunta...");
        Encuesta encuesta = new Encuesta();
        encuesta.setTitulo(textoPregunta.substring(0, Math.min(100, textoPregunta.length())));
        encuesta.setContexto("Encuesta rápida generada automáticamente");

        Pregunta pregunta = new Pregunta();
        pregunta.setTexto(textoPregunta);
        pregunta.setTipo(TipoPregunta.ABIERTA);
        pregunta.setOrden(0);
        pregunta.setEncuesta(encuesta);
        encuesta.getPreguntas().add(pregunta);

        encuestaRepository.save(encuesta);
        log.debug("[ENCUESTA-RAPIDA] Encuesta persistida con id={}", encuesta.getId());

        return new Long[]{encuesta.getId(), encuesta.getPreguntas().get(0).getId()};
    }

    @Transactional
    public Simulacion guardarSimulacion(Long encuestaId, int totalPersonas) {
        log.debug("[ENCUESTA-RAPIDA] Creando Simulacion para encuestaId={}, totalPersonas={}", encuestaId, totalPersonas);
        Encuesta encuesta = encuestaRepository.findById(encuestaId).orElseThrow();
        Simulacion simulacion = new Simulacion();
        simulacion.setEncuesta(encuesta);
        simulacion.setEstado(EstadoSimulacion.PENDIENTE);
        simulacion.setTotalRespuestas(totalPersonas);
        simulacion.setRespuestasCompletadas(0);
        Simulacion saved = simulacionRepository.save(simulacion);
        log.debug("[ENCUESTA-RAPIDA] Simulacion persistida con id={}", saved.getId());
        return saved;
    }

    @Async("simulacionExecutor")
    public void ejecutarAsync(Long simulacionId, List<Long> personaIds, Long preguntaId) {
        log.info("[SIMULACION-{}] Inicio de ejecución asíncrona. Thread: {}", simulacionId, Thread.currentThread().getName());

        Simulacion simulacion = simulacionRepository.findById(simulacionId).orElseThrow();
        simulacion.setEstado(EstadoSimulacion.CORRIENDO);
        simulacionRepository.save(simulacion);
        log.info("[SIMULACION-{}] Estado actualizado a CORRIENDO", simulacionId);

        Pregunta pregunta = encuestaRepository.findById(simulacion.getEncuesta().getId())
                .orElseThrow()
                .getPreguntas()
                .get(0);
        log.debug("[SIMULACION-{}] Pregunta cargada — id={}, texto='{}'", simulacionId, pregunta.getId(), pregunta.getTexto());

        List<Persona> personas = personaRepository.findAllByIdIn(personaIds);
        log.info("[SIMULACION-{}] {} personas cargadas para procesar", simulacionId, personas.size());

        // Procesar en lotes de 5 para no exceder rate limit
        int batchSize = 3;
        int totalLotes = (int) Math.ceil((double) personas.size() / batchSize);

        for (int i = 0; i < personas.size(); i += batchSize) {
            int loteNum = (i / batchSize) + 1;
            List<Persona> lote = personas.subList(i, Math.min(i + batchSize, personas.size()));
            log.info("[SIMULACION-{}] Procesando lote {}/{} ({} personas)", simulacionId, loteNum, totalLotes, lote.size());

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Persona persona : lote) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    log.debug("[SIMULACION-{}] Llamando a Claude para persona id={} ({})", simulacionId, persona.getId(), persona.getNombre());
                    try {
                        String respuestaTexto = claudeService.responderPregunta(persona, pregunta, null);
                        log.debug("[SIMULACION-{}] Respuesta obtenida para persona id={}", simulacionId, persona.getId());
                        Respuesta respuesta = new Respuesta();
                        respuesta.setSimulacion(simulacion);
                        respuesta.setPersona(persona);
                        respuesta.setPregunta(pregunta);
                        respuesta.setRespuestaTexto(respuestaTexto);
                        guardarYAvanzar(simulacionId, respuesta);
                    } catch (Exception e) {
                        log.error("[SIMULACION-{}] Error al procesar persona id={}: {}", simulacionId, persona.getId(), e.getMessage(), e);
                        Respuesta error = new Respuesta();
                        error.setSimulacion(simulacion);
                        error.setPersona(persona);
                        error.setPregunta(pregunta);
                        error.setRespuestaTexto("[Error al obtener respuesta]");
                        guardarYAvanzar(simulacionId, error);
                    }
                });
                futures.add(future);
            }

            // Esperar que termine el lote antes del siguiente
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("[SIMULACION-{}] Lote {}/{} completado", simulacionId, loteNum, totalLotes);

            // Pausa entre lotes para respetar rate limit
            if (i + batchSize < personas.size()) {
                try {
                    log.debug("[SIMULACION-{}] Pausa de 1s antes del siguiente lote...", simulacionId);
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        Simulacion finalizada = simulacionRepository.findById(simulacionId).orElseThrow();
        finalizada.setEstado(EstadoSimulacion.COMPLETA);
        finalizada.setFinalizadoEn(LocalDateTime.now());
        simulacionRepository.save(finalizada);

        log.info("[SIMULACION-{}] COMPLETA. {}/{} respuestas guardadas.", simulacionId,
                finalizada.getRespuestasCompletadas(), finalizada.getTotalRespuestas());
    }

    @Transactional
    synchronized void guardarYAvanzar(Long simulacionId, Respuesta respuesta) {
        respuestaRepository.save(respuesta);
        Simulacion sim = simulacionRepository.findById(simulacionId).orElseThrow();
        sim.setRespuestasCompletadas(sim.getRespuestasCompletadas() + 1);
        simulacionRepository.save(sim);
        log.debug("[SIMULACION-{}] Progreso: {}/{}", simulacionId,
                sim.getRespuestasCompletadas(), sim.getTotalRespuestas());
    }

    public List<Respuesta> obtenerRespuestas(Long simulacionId) {
        return respuestaRepository.findBySimulacionId(simulacionId);
    }
}
