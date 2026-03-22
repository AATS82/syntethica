package com.synthetica.repository;

import com.synthetica.model.Simulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulacionRepository extends JpaRepository<Simulacion, Long> {
    List<Simulacion> findByEncuestaIdOrderByCreadoEnDesc(Long encuestaId);
}
