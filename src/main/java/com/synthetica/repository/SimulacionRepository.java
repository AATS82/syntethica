package com.synthetica.repository;

import com.synthetica.model.Simulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SimulacionRepository extends JpaRepository<Simulacion, Long> {

    List<Simulacion> findByEncuestaIdOrderByCreadoEnDesc(Long encuestaId);

    List<Simulacion> findTop10ByEncuestaUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    @Query("SELECT e.usuario.id FROM Simulacion s JOIN s.encuesta e WHERE s.id = :simulacionId")
    Optional<Long> findUsuarioIdBySimulacionId(@Param("simulacionId") Long simulacionId);

    @Query("SELECT e.usuario.email FROM Simulacion s JOIN s.encuesta e WHERE s.id = :simulacionId")
    Optional<String> findUsuarioEmailBySimulacionId(@Param("simulacionId") Long simulacionId);

    @Modifying
    @Query("UPDATE Simulacion s SET s.respuestasCompletadas = s.respuestasCompletadas + 1 WHERE s.id = :id")
    void incrementarRespuestasCompletadas(@Param("id") Long id);
}
