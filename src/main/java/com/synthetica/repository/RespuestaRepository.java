package com.synthetica.repository;

import com.synthetica.model.Respuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RespuestaRepository extends JpaRepository<Respuesta, Long> {

    @Query("SELECT r FROM Respuesta r WHERE r.simulacion.id = :simulacionId ORDER BY r.id ASC")
    List<Respuesta> findBySimulacionIdOrdenado(Long simulacionId);

    List<Respuesta> findBySimulacionId(Long simulacionId);

    // Para obtener resultados agrupados por pregunta
    @Query("SELECT r FROM Respuesta r WHERE r.simulacion.id = :simulacionId AND r.pregunta.id = :preguntaId")
    List<Respuesta> findBySimulacionIdAndPreguntaId(Long simulacionId, Long preguntaId);
}
