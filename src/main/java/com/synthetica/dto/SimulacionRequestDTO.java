package com.synthetica.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SimulacionRequestDTO {

    @NotNull(message = "El ID de encuesta es obligatorio")
    private Long encuestaId;

    @NotEmpty(message = "Debes seleccionar al menos una persona")
    private List<Long> personaIds;

    public Long getEncuestaId() {
        return encuestaId;
    }

    public void setEncuestaId(Long encuestaId) {
        this.encuestaId = encuestaId;
    }

    public List<Long> getPersonaIds() {
        return personaIds;
    }

    public void setPersonaIds(List<Long> personaIds) {
        this.personaIds = personaIds;
    }
}
