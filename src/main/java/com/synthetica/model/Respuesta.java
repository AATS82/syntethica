package com.synthetica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "respuestas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Respuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulacion_id", nullable = false)
    @JsonIgnore
    private Simulacion simulacion;

    // Nullable: null cuando se usa un perfil sintético en vez de una Persona de BD
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = true)
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

    // ── Datos del perfil sintético (null si se usó una Persona de BD) ─────────

    @Column(name = "perfil_resumen")
    private String perfilResumen;

    @Column(name = "perfil_nombre")
    private String perfilNombre;

    @Column(name = "perfil_edad")
    private Integer perfilEdad;

    @Column(name = "perfil_sexo")
    private String perfilSexo;

    @Column(name = "perfil_ciudad")
    private String perfilCiudad;

    @Column(name = "perfil_pais")
    private String perfilPais;

    @Column(name = "perfil_educacion")
    private String perfilEducacion;

    @Column(name = "perfil_ocupacion")
    private String perfilOcupacion;

    @Column(name = "perfil_nse")
    private String perfilNse;

    public Respuesta() {
    }

    // ── Helpers demográficos — AnalisisService usa estos en vez de getPersona() ─

    public String getNombreEfectivo() {
        return persona != null ? persona.getNombre() : perfilNombre;
    }

    public Integer getEdadEfectiva() {
        return persona != null ? persona.getEdad() : perfilEdad;
    }

    public String getSexoEfectivo() {
        return persona != null ? persona.getSexo() : perfilSexo;
    }

    public String getCiudadEfectiva() {
        return persona != null ? persona.getCiudad() : perfilCiudad;
    }

    public String getPaisEfectivo() {
        return persona != null ? persona.getPais() : perfilPais;
    }

    public String getEducacionEfectiva() {
        return persona != null ? persona.getEducacion() : perfilEducacion;
    }

    public String getOcupacionEfectiva() {
        return persona != null ? persona.getOcupacion() : perfilOcupacion;
    }

    public String getNseEfectivo() {
        return persona != null ? persona.getNivelSocioeconomico() : perfilNse;
    }

    /** Descripción de perfil para mostrar en resultados (API y análisis). */
    public String getPerfilDescripcion() {
        if (perfilResumen != null && !perfilResumen.isBlank()) return perfilResumen;
        if (persona != null) {
            return persona.getNombre() + ", " + persona.getEdad() + " años, "
                    + persona.getCiudad() + ", " + persona.getNivelSocioeconomico();
        }
        return "Perfil sintético";
    }

    // ── Getters y setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Simulacion getSimulacion() { return simulacion; }
    public void setSimulacion(Simulacion simulacion) { this.simulacion = simulacion; }

    public Persona getPersona() { return persona; }
    public void setPersona(Persona persona) { this.persona = persona; }

    public Pregunta getPregunta() { return pregunta; }
    public void setPregunta(Pregunta pregunta) { this.pregunta = pregunta; }

    public String getRespuestaTexto() { return respuestaTexto; }
    public void setRespuestaTexto(String respuestaTexto) { this.respuestaTexto = respuestaTexto; }

    public Integer getValorLikert() { return valorLikert; }
    public void setValorLikert(Integer valorLikert) { this.valorLikert = valorLikert; }

    public LocalDateTime getRespondidoEn() { return respondidoEn; }
    public void setRespondidoEn(LocalDateTime respondidoEn) { this.respondidoEn = respondidoEn; }

    public String getPerfilResumen() { return perfilResumen; }
    public void setPerfilResumen(String perfilResumen) { this.perfilResumen = perfilResumen; }

    public String getPerfilNombre() { return perfilNombre; }
    public void setPerfilNombre(String perfilNombre) { this.perfilNombre = perfilNombre; }

    public Integer getPerfilEdad() { return perfilEdad; }
    public void setPerfilEdad(Integer perfilEdad) { this.perfilEdad = perfilEdad; }

    public String getPerfilSexo() { return perfilSexo; }
    public void setPerfilSexo(String perfilSexo) { this.perfilSexo = perfilSexo; }

    public String getPerfilCiudad() { return perfilCiudad; }
    public void setPerfilCiudad(String perfilCiudad) { this.perfilCiudad = perfilCiudad; }

    public String getPerfilPais() { return perfilPais; }
    public void setPerfilPais(String perfilPais) { this.perfilPais = perfilPais; }

    public String getPerfilEducacion() { return perfilEducacion; }
    public void setPerfilEducacion(String perfilEducacion) { this.perfilEducacion = perfilEducacion; }

    public String getPerfilOcupacion() { return perfilOcupacion; }
    public void setPerfilOcupacion(String perfilOcupacion) { this.perfilOcupacion = perfilOcupacion; }

    public String getPerfilNse() { return perfilNse; }
    public void setPerfilNse(String perfilNse) { this.perfilNse = perfilNse; }
}
