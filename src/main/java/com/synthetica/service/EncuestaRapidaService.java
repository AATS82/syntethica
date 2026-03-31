package com.synthetica.service;

import com.synthetica.dto.EncuestaRapidaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.model.Pregunta;
import com.synthetica.model.Respuesta;
import com.synthetica.model.Simulacion;
import com.synthetica.model.enums.EstadoSimulacion;
import com.synthetica.model.enums.TipoPregunta;
import com.synthetica.model.Usuario;
import com.synthetica.repository.EncuestaRepository;
import com.synthetica.repository.RespuestaRepository;
import com.synthetica.repository.SimulacionRepository;
import com.synthetica.repository.UsuarioRepository;
import com.synthetica.service.PoblacionService.PerfilSintetico;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncuestaRapidaService {

    private static final Logger log = LoggerFactory.getLogger(EncuestaRapidaService.class);

    private final PoblacionService poblacionService;
    private final EncuestaRepository encuestaRepository;
    private final SimulacionRepository simulacionRepository;
    private final RespuestaRepository respuestaRepository;
    private final ClaudeService claudeService;
    private final UsuarioRepository usuarioRepository;

    public EncuestaRapidaService(PoblacionService poblacionService,
            EncuestaRepository encuestaRepository,
            SimulacionRepository simulacionRepository,
            RespuestaRepository respuestaRepository,
            ClaudeService claudeService,
            UsuarioRepository usuarioRepository) {
        this.poblacionService = poblacionService;
        this.encuestaRepository = encuestaRepository;
        this.simulacionRepository = simulacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.claudeService = claudeService;
        this.usuarioRepository = usuarioRepository;
    }

    // Sin @Transactional aquí
    public Simulacion iniciar(EncuestaRapidaRequestDTO dto, Long usuarioId) {
        log.info("[ENCUESTA-RAPIDA] Iniciando encuesta rápida. Pregunta: '{}'", dto.getPregunta());

        var filtros = dto.getFiltros();
        int cantidad = dto.getCantidad() != null ? dto.getCantidad() : 50;

        if (cantidad <= 0 || cantidad > 500) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y 500");
        }

        // Validar y descontar créditos antes de iniciar
        descontarCreditos(usuarioId, cantidad);
        log.info("[ENCUESTA-RAPIDA] {} créditos descontados al usuario {}", cantidad, usuarioId);

        if (filtros != null) {
            log.info("[ENCUESTA-RAPIDA] Filtros aplicados — pais={}, sexo={}, edad={}-{}, educacion={}, nse={}",
                    filtros.getPais(), filtros.getSexoFiltro(),
                    filtros.getEdadMin(), filtros.getEdadMax(),
                    filtros.getEducacion(), filtros.getNivelSocioeconomico());
        } else {
            log.info("[ENCUESTA-RAPIDA] Sin filtros, se samplearán perfiles sin restricciones");
        }

        log.info("[ENCUESTA-RAPIDA] Cantidad solicitada: {}", cantidad);

        // Samplear perfiles estadísticos en memoria — sin consulta a BD
        List<PerfilSintetico> perfiles = poblacionService.samplearPerfiles(filtros, cantidad);
        log.info("[ENCUESTA-RAPIDA] {} perfiles sintéticos generados", perfiles.size());

        // Guardar encuesta y pregunta en transacción propia — commit inmediato
        log.debug("[ENCUESTA-RAPIDA] Guardando encuesta y pregunta en BD...");
        Long[] ids = guardarEncuestaYPregunta(dto.getPregunta(), usuarioId);
        Long encuestaId = ids[0];
        Long preguntaId = ids[1];
        log.info("[ENCUESTA-RAPIDA] Encuesta guardada — encuestaId={}, preguntaId={}", encuestaId, preguntaId);

        // Guardar simulación en transacción propia — commit inmediato
        log.debug("[ENCUESTA-RAPIDA] Guardando simulación para encuestaId={}...", encuestaId);
        Simulacion simulacion = guardarSimulacion(encuestaId, perfiles.size());
        log.info("[ENCUESTA-RAPIDA] Simulación creada — simulacionId={}, estado={}, total={}",
                simulacion.getId(), simulacion.getEstado(), simulacion.getTotalRespuestas());

        // Ahora sí lanzar async — todo está commiteado en BD
        log.info("[ENCUESTA-RAPIDA] Lanzando ejecución asíncrona para simulacionId={}", simulacion.getId());
        ejecutarAsync(simulacion.getId(), perfiles, preguntaId);

        return simulacion;
    }

    @Transactional
    public void descontarCreditos(Long usuarioId, int cantidad) {
        // Lock pesimista para evitar race condition si el usuario lanza varias encuestas simultáneas
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        int disponibles = usuario.getCreditosTotal() - usuario.getCreditosUsados();
        if (disponibles < cantidad) {
            throw new IllegalStateException(
                    "Créditos insuficientes. Disponibles: " + disponibles + ", requeridos: " + cantidad);
        }
        usuario.setCreditosUsados(usuario.getCreditosUsados() + cantidad);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public Long[] guardarEncuestaYPregunta(String textoPregunta, Long usuarioId) {
        log.debug("[ENCUESTA-RAPIDA] Creando entidades Encuesta y Pregunta...");
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        Encuesta encuesta = new Encuesta();
        encuesta.setUsuario(usuario);
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
    public Simulacion guardarSimulacion(Long encuestaId, int totalPerfiles) {
        log.debug("[ENCUESTA-RAPIDA] Creando Simulacion para encuestaId={}, total={}", encuestaId, totalPerfiles);
        Encuesta encuesta = encuestaRepository.findById(encuestaId).orElseThrow();
        Simulacion simulacion = new Simulacion();
        simulacion.setEncuesta(encuesta);
        simulacion.setEstado(EstadoSimulacion.PENDIENTE);
        simulacion.setTotalRespuestas(totalPerfiles);
        simulacion.setRespuestasCompletadas(0);
        Simulacion saved = simulacionRepository.save(simulacion);
        log.debug("[ENCUESTA-RAPIDA] Simulacion persistida con id={}", saved.getId());
        return saved;
    }

    @Async("simulacionExecutor")
    public void ejecutarAsync(Long simulacionId, List<PerfilSintetico> perfiles, Long preguntaId) {
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

        log.info("[SIMULACION-{}] {} perfiles sintéticos a procesar", simulacionId, perfiles.size());

        try {
            int batchSize = 3;
            int totalLotes = (int) Math.ceil((double) perfiles.size() / batchSize);

            for (int i = 0; i < perfiles.size(); i += batchSize) {
                int loteNum = (i / batchSize) + 1;
                List<PerfilSintetico> lote = perfiles.subList(i, Math.min(i + batchSize, perfiles.size()));
                log.info("[SIMULACION-{}] Procesando lote {}/{} ({} perfiles)", simulacionId, loteNum, totalLotes, lote.size());

                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (PerfilSintetico perfil : lote) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        log.debug("[SIMULACION-{}] Llamando a Claude para perfil: {}", simulacionId, perfil.getNombre());
                        try {
                            String respuestaTexto = claudeService.responderPregunta(perfil, pregunta, null);
                            log.debug("[SIMULACION-{}] Respuesta obtenida para: {}", simulacionId, perfil.getNombre());
                            Respuesta respuesta = crearRespuestaSintetica(simulacion, pregunta, perfil, respuestaTexto);
                            guardarYAvanzar(simulacionId, respuesta);
                        } catch (Exception e) {
                            log.error("[SIMULACION-{}] Error al procesar perfil {}: {}", simulacionId, perfil.getNombre(), e.getMessage(), e);
                            Respuesta error = crearRespuestaSintetica(simulacion, pregunta, perfil, "[Error al obtener respuesta]");
                            guardarYAvanzar(simulacionId, error);
                        }
                    });
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, TimeUnit.MINUTES);
                log.info("[SIMULACION-{}] Lote {}/{} completado", simulacionId, loteNum, totalLotes);

                if (i + batchSize < perfiles.size()) {
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

        } catch (Exception e) {
            log.error("[SIMULACION-{}] Error fatal en ejecución async: {}", simulacionId, e.getMessage());
            Simulacion fallida = simulacionRepository.findById(simulacionId).orElseThrow();
            fallida.setEstado(EstadoSimulacion.ERROR);
            fallida.setFinalizadoEn(LocalDateTime.now());
            simulacionRepository.save(fallida);
        }
    }

    private Respuesta crearRespuestaSintetica(Simulacion simulacion, Pregunta pregunta,
            PerfilSintetico perfil, String texto) {
        Respuesta r = new Respuesta();
        r.setSimulacion(simulacion);
        r.setPregunta(pregunta);
        r.setRespuestaTexto(texto);
        // Persistir los datos demográficos del perfil efímero
        r.setPerfilResumen(perfil.toResumen());
        r.setPerfilNombre(perfil.getNombre());
        r.setPerfilEdad(perfil.getEdad());
        r.setPerfilSexo(perfil.getSexo());
        r.setPerfilCiudad(perfil.getCiudad());
        r.setPerfilPais(perfil.getPais());
        r.setPerfilEducacion(perfil.getEducacion());
        r.setPerfilOcupacion(perfil.getOcupacion());
        r.setPerfilNse(perfil.getNivelSocioeconomico());
        return r;
    }

    @Transactional
    void guardarYAvanzar(Long simulacionId, Respuesta respuesta) {
        respuestaRepository.save(respuesta);
        simulacionRepository.incrementarRespuestas(simulacionId);
        log.debug("[SIMULACION-{}] Respuesta guardada para perfil: {}", simulacionId,
                respuesta.getPerfilNombre());
    }

    public List<Respuesta> obtenerRespuestas(Long simulacionId) {
        return respuestaRepository.findBySimulacionId(simulacionId);
    }
}
