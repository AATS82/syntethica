package com.synthetica.service;

import com.synthetica.model.Respuesta;
import com.synthetica.repository.RespuestaRepository;
import com.synthetica.repository.SimulacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RespuestaService {

    private final RespuestaRepository respuestaRepository;
    private final SimulacionRepository simulacionRepository;

    public RespuestaService(RespuestaRepository respuestaRepository,
                            SimulacionRepository simulacionRepository) {
        this.respuestaRepository = respuestaRepository;
        this.simulacionRepository = simulacionRepository;
    }

    @Transactional
    public void guardarYAvanzar(Long simulacionId, Respuesta respuesta) {
        respuestaRepository.save(respuesta);
        simulacionRepository.incrementarRespuestasCompletadas(simulacionId);
    }
}
