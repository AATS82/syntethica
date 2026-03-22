package com.synthetica.dto;

import com.synthetica.model.enums.TipoPregunta;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;


public class EncuestaRequestDTO {

    @NotBlank
    private String titulo;
    private String descripcion;
    private String contexto;
    private List<PreguntaRequestDTO> preguntas = new ArrayList<>();


    public static class PreguntaRequestDTO {
        @NotBlank
        private String texto;
        private TipoPregunta tipo = TipoPregunta.ABIERTA;
        private Integer orden = 0;

        public String getTexto() {
            return texto;
        }

        public void setTexto(String texto) {
            this.texto = texto;
        }

        public TipoPregunta getTipo() {
            return tipo;
        }

        public void setTipo(TipoPregunta tipo) {
            this.tipo = tipo;
        }

        public Integer getOrden() {
            return orden;
        }

        public void setOrden(Integer orden) {
            this.orden = orden;
        }
        
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getContexto() {
        return contexto;
    }

    public void setContexto(String contexto) {
        this.contexto = contexto;
    }

    public List<PreguntaRequestDTO> getPreguntas() {
        return preguntas;
    }

    public void setPreguntas(List<PreguntaRequestDTO> preguntas) {
        this.preguntas = preguntas;
    }
    
}
