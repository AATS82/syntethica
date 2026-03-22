package com.synthetica.service;

import com.synthetica.dto.EncuestaRequestDTO;
import com.synthetica.model.Encuesta;
import com.synthetica.model.Pregunta;
import com.synthetica.repository.EncuestaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class EncuestaService {

    private final EncuestaRepository encuestaRepository;
    
    public EncuestaService(EncuestaRepository encuestaRepository) {
    this.encuestaRepository = encuestaRepository;
}

    public List<Encuesta> listarTodas() {
        return encuestaRepository.findAll();
    }

    public Encuesta obtenerPorId(Long id) {
        return encuestaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Encuesta no encontrada: " + id));
    }

    @Transactional
    public Encuesta crear(EncuestaRequestDTO dto) {
        Encuesta encuesta = new Encuesta();
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
    public Encuesta actualizar(Long id, EncuestaRequestDTO dto) {
        Encuesta encuesta = obtenerPorId(id);
        encuesta.setTitulo(dto.getTitulo());
        encuesta.setDescripcion(dto.getDescripcion());
        encuesta.setContexto(dto.getContexto());

        // Reemplazar preguntas completo
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

    public void eliminar(Long id) {
        encuestaRepository.deleteById(id);
    }
}
