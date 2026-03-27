package com.synthetica.service;

import com.synthetica.dto.EncuestaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.model.Pregunta;
import com.synthetica.model.Usuario;
import com.synthetica.repository.EncuestaRepository;
import com.synthetica.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class EncuestaService {

    private final EncuestaRepository encuestaRepository;
    private final UsuarioRepository usuarioRepository;

    public EncuestaService(EncuestaRepository encuestaRepository,
                           UsuarioRepository usuarioRepository) {
        this.encuestaRepository = encuestaRepository;
        this.usuarioRepository = usuarioRepository;
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
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        Encuesta encuesta = new Encuesta();
        encuesta.setUsuario(usuario);
        encuesta.setTitulo(dto.getTitulo());
        encuesta.setDescripcion(dto.getDescripcion());
        encuesta.setContexto(dto.getContexto());

        if (dto.getPreguntas() != null) {
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

        return encuestaRepository.save(encuesta);
    }

    @Transactional
    public Encuesta actualizar(Long id, EncuestaRequestDTO dto, Long usuarioId) {
        Encuesta encuesta = obtenerPorId(id);

        if (encuesta.getUsuario() == null || !encuesta.getUsuario().getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para editar esta encuesta");
        }

        encuesta.setTitulo(dto.getTitulo());
        encuesta.setDescripcion(dto.getDescripcion());
        encuesta.setContexto(dto.getContexto());

        encuesta.getPreguntas().clear();
        if (dto.getPreguntas() != null) {
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

        return encuestaRepository.save(encuesta);
    }

    public void eliminar(Long id, Long usuarioId) {
        Encuesta encuesta = obtenerPorId(id);

        if (encuesta.getUsuario() == null || !encuesta.getUsuario().getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para eliminar esta encuesta");
        }

        encuestaRepository.deleteById(id);
    }
}
