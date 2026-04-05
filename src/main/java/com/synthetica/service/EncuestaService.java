package com.synthetica.service;

import com.synthetica.dto.EncuestaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.model.Pregunta;
import com.synthetica.model.Usuario;
import com.synthetica.repository.EncuestaRepository;
import com.synthetica.repository.SimulacionRepository;
import com.synthetica.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class EncuestaService {

    private static final Logger log = LoggerFactory.getLogger(EncuestaService.class);

    private final EncuestaRepository encuestaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimulacionRepository simulacionRepository;

    public EncuestaService(EncuestaRepository encuestaRepository,
                           UsuarioRepository usuarioRepository,
                           SimulacionRepository simulacionRepository) {
        this.encuestaRepository = encuestaRepository;
        this.usuarioRepository = usuarioRepository;
        this.simulacionRepository = simulacionRepository;
    }

    public List<Encuesta> listarPorUsuario(Long usuarioId) {
        return encuestaRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    public Encuesta obtenerPorId(Long id) {
        return encuestaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Encuesta no encontrada: " + id));
    }

    @Transactional
    public Encuesta crear(EncuestaRequestDTO dto, Long usuarioId) {
        log.info("[ENCUESTA] Creando encuesta para usuarioId={}, titulo='{}'", usuarioId, dto.getTitulo());
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        Encuesta encuesta = new Encuesta();
        encuesta.setUsuario(usuario);
        encuesta.setTitulo(dto.getTitulo());
        encuesta.setDescripcion(dto.getDescripcion());
        encuesta.setContexto(dto.getContexto());

        if (dto.getPreguntas() != null) {
            log.debug("[ENCUESTA] Agregando {} preguntas a la encuesta", dto.getPreguntas().size());
            List<Pregunta> preguntas = new ArrayList<>();
            for (int i = 0; i < dto.getPreguntas().size(); i++) {
                var p = dto.getPreguntas().get(i);
                Pregunta pregunta = new Pregunta();
                pregunta.setTexto(p.getTexto());
                pregunta.setTipo(p.getTipo());
                pregunta.setOrden(p.getOrden() != null ? p.getOrden() : i);
                pregunta.setEncuesta(encuesta);
                preguntas.add(pregunta);
            }
            encuesta.setPreguntas(preguntas);
        }

        Encuesta saved = encuestaRepository.save(encuesta);
        log.info("[ENCUESTA] Encuesta creada con id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Encuesta actualizar(Long id, EncuestaRequestDTO dto, Long usuarioId) {
        log.info("[ENCUESTA] Actualizando encuesta id={} para usuarioId={}", id, usuarioId);
        Encuesta encuesta = obtenerPorId(id);

        if (encuesta.getUsuario() == null || !encuesta.getUsuario().getId().equals(usuarioId)) {
            log.warn("[ENCUESTA] Acceso denegado — usuarioId={} intentó editar encuesta id={}", usuarioId, id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para editar esta encuesta");
        }

        encuesta.setTitulo(dto.getTitulo());
        encuesta.setDescripcion(dto.getDescripcion());
        encuesta.setContexto(dto.getContexto());

        encuesta.getPreguntas().clear();
        if (dto.getPreguntas() != null) {
            log.debug("[ENCUESTA] Reemplazando preguntas con {} nuevas", dto.getPreguntas().size());
            for (int i = 0; i < dto.getPreguntas().size(); i++) {
                var p = dto.getPreguntas().get(i);
                Pregunta pregunta = new Pregunta();
                pregunta.setTexto(p.getTexto());
                pregunta.setTipo(p.getTipo());
                pregunta.setOrden(p.getOrden() != null ? p.getOrden() : i);
                pregunta.setEncuesta(encuesta);
                encuesta.getPreguntas().add(pregunta);
            }
        }

        Encuesta saved = encuestaRepository.save(encuesta);
        log.info("[ENCUESTA] Encuesta id={} actualizada correctamente", saved.getId());
        return saved;
    }

    @Transactional
    public void eliminar(Long id, Long usuarioId) {
        log.info("[ENCUESTA] Eliminando encuesta id={} para usuarioId={}", id, usuarioId);
        Encuesta encuesta = obtenerPorId(id);

        if (encuesta.getUsuario() == null || !encuesta.getUsuario().getId().equals(usuarioId)) {
            log.warn("[ENCUESTA] Acceso denegado — usuarioId={} intentó eliminar encuesta id={}", usuarioId, id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar esta encuesta");
        }

        // Eliminar simulaciones primero (sus respuestas se eliminan por cascade en Simulacion)
        simulacionRepository.deleteAll(
                simulacionRepository.findByEncuestaIdOrderByCreadoEnDesc(id));

        encuestaRepository.deleteById(id);
        log.info("[ENCUESTA] Encuesta id={} y sus simulaciones eliminadas", id);
    }
}
