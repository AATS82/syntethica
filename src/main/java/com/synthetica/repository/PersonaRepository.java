package com.synthetica.repository;

import com.synthetica.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {

    List<Persona> findAllByIdIn(List<Long> ids);

    @Query(value = """
        SELECT * FROM personas
        WHERE (:pais IS NULL OR pais = :pais)
        AND (:sexo IS NULL OR sexo = :sexo)
        AND (:edadMin IS NULL OR edad >= :edadMin)
        AND (:edadMax IS NULL OR edad <= :edadMax)
        AND (:educacion IS NULL OR educacion = :educacion)
        AND (:nse IS NULL OR nivel_socioeconomico = :nse)
        ORDER BY RANDOM()
        LIMIT :cantidad
        """, nativeQuery = true)
    List<Persona> findByFiltros(
            @Param("pais") String pais,
            @Param("sexo") String sexo,
            @Param("edadMin") Integer edadMin,
            @Param("edadMax") Integer edadMax,
            @Param("educacion") String educacion,
            @Param("nse") String nse,
            @Param("cantidad") int cantidad
    );
}
