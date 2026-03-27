package com.synthetica.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "personas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nombre;

    @Min(10)
    @Max(100)
    @Column(nullable = false)
    private Integer edad;

    @NotBlank
    private String sexo;           // Masculino / Femenino / No binario

    @NotBlank
    private String pais;

    private String ciudad;

    @NotBlank
    private String educacion;      // Básica / Media / Técnica / Universitaria / Postgrado

    @NotBlank
    private String ocupacion;

    // Rasgos de personalidad en texto libre
    // Ej: "Introvertida, pragmática, desconfiada de las marcas extranjeras"
    @Column(columnDefinition = "TEXT")
    private String personalidad;

    // Nivel socioeconómico: Bajo / Medio-bajo / Medio / Medio-alto / Alto
    private String nivelSocioeconomico;

    @Column(updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    // ── Método helper para construir el contexto del system prompt ────────────
    @Transient
    public String toPromptContext() {
        return String.format(
                "Eres %s, %d años, %s, de %s%s. "
                + "Nivel educacional: %s. Ocupación: %s. "
                + "Nivel socioeconómico: %s. "
                + "Personalidad y rasgos: %s.",
                nombre, edad, sexo,
                ciudad != null ? ciudad + ", " : "", pais,
                educacion, ocupacion,
                nivelSocioeconomico != null ? nivelSocioeconomico : "Medio",
                personalidad != null ? personalidad : "no especificada"
        );
    }

    public Persona() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getEducacion() {
        return educacion;
    }

    public void setEducacion(String educacion) {
        this.educacion = educacion;
    }

    public String getOcupacion() {
        return ocupacion;
    }

    public void setOcupacion(String ocupacion) {
        this.ocupacion = ocupacion;
    }

    public String getPersonalidad() {
        return personalidad;
    }

    public void setPersonalidad(String personalidad) {
        this.personalidad = personalidad;
    }

    public String getNivelSocioeconomico() {
        return nivelSocioeconomico;
    }

    public void setNivelSocioeconomico(String nivelSocioeconomico) {
        this.nivelSocioeconomico = nivelSocioeconomico;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

}
