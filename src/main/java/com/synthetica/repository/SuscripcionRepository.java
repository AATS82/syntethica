package com.synthetica.repository;

import com.synthetica.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    boolean existsByTbkToken(String tbkToken);

    List<Suscripcion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
