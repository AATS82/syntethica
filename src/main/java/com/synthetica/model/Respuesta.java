package com.synthetica.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "respuestas")
public class Respuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulacion_id", nullable = false)
    private Simulacion simulacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private Pregunta pregunta;

    // Para preguntas ABIERTA
    @Column(columnDefinition = "TEXT")
    private String respuestaTexto;

    // Para preguntas LIKERT (1-5), null si es ABIERTA
    private Integer valorLikert;

    private LocalDateTime respondidoEn = LocalDateTime.now();

    public Respuesta() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Simulacion getSimulacion() {
        return simulacion;
    }

    public void setSimulacion(Simulacion simulacion) {
        this.simulacion = simulacion;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public Pregunta getPregunta() {
        return pregunta;
    }

    public void setPregunta(Pregunta pregunta) {
        this.pregunta = pregunta;
    }

    public String getRespuestaTexto() {
        return respuestaTexto;
    }

    public void setRespuestaTexto(String respuestaTexto) {
        this.respuestaTexto = respuestaTexto;
    }

    public Integer getValorLikert() {
        return valorLikert;
    }

    public void setValorLikert(Integer valorLikert) {
        this.valorLikert = valorLikert;
    }

    public LocalDateTime getRespondidoEn() {
        return respondidoEn;
    }

    public void setRespondidoEn(LocalDateTime respondidoEn) {
        this.respondidoEn = respondidoEn;
    }

}
