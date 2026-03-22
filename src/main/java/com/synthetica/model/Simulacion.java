package com.synthetica.model;

import com.synthetica.model.enums.EstadoSimulacion;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "simulaciones")
public class Simulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encuesta_id", nullable = false)
    private Encuesta encuesta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSimulacion estado = EstadoSimulacion.PENDIENTE;

    // Total de respuestas esperadas (personas × preguntas)
    private Integer totalRespuestas = 0;

    // Respuestas completadas hasta ahora (para progreso)
    private Integer respuestasCompletadas = 0;

    @Column(updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    private LocalDateTime finalizadoEn;

    @OneToMany(mappedBy = "simulacion", cascade = CascadeType.ALL)
    private List<Respuesta> respuestas = new ArrayList<>();

    // ── Helper para calcular % de progreso ────────────────────────────────────
    @Transient
    public int getPorcentajeProgreso() {
        if (totalRespuestas == null || totalRespuestas == 0) return 0;
        return (int) ((respuestasCompletadas * 100.0) / totalRespuestas);
    }

    public Simulacion() {
    }
    

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Encuesta getEncuesta() {
        return encuesta;
    }

    public void setEncuesta(Encuesta encuesta) {
        this.encuesta = encuesta;
    }

    public EstadoSimulacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoSimulacion estado) {
        this.estado = estado;
    }

    public Integer getTotalRespuestas() {
        return totalRespuestas;
    }

    public void setTotalRespuestas(Integer totalRespuestas) {
        this.totalRespuestas = totalRespuestas;
    }

    public Integer getRespuestasCompletadas() {
        return respuestasCompletadas;
    }

    public void setRespuestasCompletadas(Integer respuestasCompletadas) {
        this.respuestasCompletadas = respuestasCompletadas;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getFinalizadoEn() {
        return finalizadoEn;
    }

    public void setFinalizadoEn(LocalDateTime finalizadoEn) {
        this.finalizadoEn = finalizadoEn;
    }

    public List<Respuesta> getRespuestas() {
        return respuestas;
    }

    public void setRespuestas(List<Respuesta> respuestas) {
        this.respuestas = respuestas;
    }
    
}
