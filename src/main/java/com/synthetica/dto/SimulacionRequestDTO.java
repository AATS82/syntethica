package com.synthetica.dto;

import java.util.List;

public class SimulacionRequestDTO {

    private Long encuestaId;
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
