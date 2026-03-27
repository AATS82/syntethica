/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.synthetica.dto;

/**
 *
 * @author Totoralillo
 */
public class EncuestaRapidaRequestDTO {

    private String pregunta;
    private Integer cantidad = 50;
    private FiltrosDTO filtros;

    public static class FiltrosDTO {

        private String pais;
        private String sexoFiltro;
        private Integer edadMin;
        private Integer edadMax;
        private String educacion;
        private String nivelSocioeconomico;

        public String getPais() {
            return pais;
        }

        public void setPais(String pais) {
            this.pais = pais;
        }

        public String getSexoFiltro() {
            return sexoFiltro;
        }

        public void setSexoFiltro(String sexoFiltro) {
            this.sexoFiltro = sexoFiltro;
        }

        public Integer getEdadMin() {
            return edadMin;
        }

        public void setEdadMin(Integer edadMin) {
            this.edadMin = edadMin;
        }

        public Integer getEdadMax() {
            return edadMax;
        }

        public void setEdadMax(Integer edadMax) {
            this.edadMax = edadMax;
        }

        public String getEducacion() {
            return educacion;
        }

        public void setEducacion(String educacion) {
            this.educacion = educacion;
        }

        public String getNivelSocioeconomico() {
            return nivelSocioeconomico;
        }

        public void setNivelSocioeconomico(String nivelSocioeconomico) {
            this.nivelSocioeconomico = nivelSocioeconomico;
        }

    }

    public String getPregunta() {
        return pregunta;
    }

    public void setPregunta(String pregunta) {
        this.pregunta = pregunta;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public FiltrosDTO getFiltros() {
        return filtros;
    }

    public void setFiltros(FiltrosDTO filtros) {
        this.filtros = filtros;
    }

}
