package com.synthetica.repository;

import com.synthetica.model.Simulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SimulacionRepository extends JpaRepository<Simulacion, Long> {

    List<Simulacion> findByEncuestaIdOrderByCreadoEnDesc(Long encuestaId);

    List<Simulacion> findTop10ByOrderByCreadoEnDesc();

    List<Simulacion> findTop10ByEncuestaUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    @Modifying
    @Transactional
    @Query("UPDATE Simulacion s SET s.respuestasCompletadas = s.respuestasCompletadas + 1 WHERE s.id = :id")
    void incrementarRespuestas(@Param("id") Long id);
}
