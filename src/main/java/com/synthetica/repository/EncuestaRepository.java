package com.synthetica.repository;

import com.synthetica.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {

    List<Encuesta> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
