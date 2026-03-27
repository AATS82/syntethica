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
        }

        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 50;

        List<Persona> personas = personaRepository.findByFiltros(
                pais, sexo, edadMin, edadMax, educacion, nse, cantidad
        );

        if (personas.isEmpty()) {
            throw new RuntimeException("No hay personas que coincidan con los filtros seleccionados");
        }

        // Guardar encuesta y pregunta en transacción propia — commit inmediato
        Long[] ids = guardarEncuestaYPregunta(dto.getPregunta());
        Long encuestaId = ids[0];
        Long preguntaId = ids[1];

        // Guardar simulación en transacción propia — commit inmediato
        List<Long> personaIds = personas.stream().map(Persona::getId).toList();
        Simulacion simulacion = guardarSimulacion(encuestaId, personaIds.size());

        // Ahora sí lanzar async — todo está commiteado en BD
        ejecutarAsync(simulacion.getId(), personaIds, preguntaId);

        return simulacion;
    }

    @Transactional
    public Long[] guardarEncuestaYPregunta(String textoPregunta) {
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

        return new Long[]{encuesta.getId(), encuesta.getPreguntas().get(0).getId()};
    }

    @Transactional
    public Simulacion guardarSimulacion(Long encuestaId, int totalPersonas) {
        Encuesta encuesta = encuestaRepository.findById(encuestaId).orElseThrow();
        Simulacion simulacion = new Simulacion();
        simulacion.setEncuesta(encuesta);
        simulacion.setEstado(EstadoSimulacion.PENDIENTE);
        simulacion.setTotalRespuestas(totalPersonas);
        simulacion.setRespuestasCompletadas(0);
        return simulacionRepository.save(simulacion);
    }

    @Async("simulacionExecutor")
    public void ejecutarAsync(Long simulacionId, List<Long> personaIds, Long preguntaId) {

        Simulacion simulacion = simulacionRepository.findById(simulacionId).orElseThrow();
        simulacion.setEstado(EstadoSimulacion.CORRIENDO);
        simulacionRepository.save(simulacion);

        Pregunta pregunta = encuestaRepository.findById(simulacion.getEncuesta().getId())
                .orElseThrow()
                .getPreguntas()
                .get(0);

        List<Persona> personas = personaRepository.findAllByIdIn(personaIds);

        // Procesar en lotes de 5 para no exceder rate limit
        int batchSize = 3;
        for (int i = 0; i < personas.size(); i += batchSize) {
            List<Persona> lote = personas.subList(i, Math.min(i + batchSize, personas.size()));

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Persona persona : lote) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String respuestaTexto = claudeService.responderPregunta(persona, pregunta, null);
                        Respuesta respuesta = new Respuesta();
                        respuesta.setSimulacion(simulacion);
                        respuesta.setPersona(persona);
                        respuesta.setPregunta(pregunta);
                        respuesta.setRespuestaTexto(respuestaTexto);
                        guardarYAvanzar(simulacionId, respuesta);
                    } catch (Exception e) {
                        log.error("Error persona {}: {}", persona.getId(), e.getMessage());
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

            // Pausa entre lotes para respetar rate limit
            if (i + batchSize < personas.size()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }

        Simulacion finalizada = simulacionRepository.findById(simulacionId).orElseThrow();
        finalizada.setEstado(EstadoSimulacion.COMPLETA);
        finalizada.setFinalizadoEn(LocalDateTime.now());
        simulacionRepository.save(finalizada);

        log.info("Encuesta rápida {} completada. {} respuestas.", simulacionId, finalizada.getRespuestasCompletadas());
    }

    @Transactional
    synchronized void guardarYAvanzar(Long simulacionId, Respuesta respuesta) {
        respuestaRepository.save(respuesta);
        Simulacion sim = simulacionRepository.findById(simulacionId).orElseThrow();
        sim.setRespuestasCompletadas(sim.getRespuestasCompletadas() + 1);
        simulacionRepository.save(sim);
    }

    public List<Respuesta> obtenerRespuestas(Long simulacionId) {
        return respuestaRepository.findBySimulacionId(simulacionId);
    }
}
