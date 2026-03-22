package com.synthetica.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthetica.model.Persona;
import com.synthetica.repository.PersonaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final ClaudeService claudeService;
    private final ObjectMapper mapper = new ObjectMapper();
        
    
   public PersonaService(PersonaRepository personaRepository, ClaudeService claudeService) {
    this.personaRepository = personaRepository;
    this.claudeService = claudeService;
} 
    public List<Persona> listarTodas() {
        return personaRepository.findAll();
    }

    public Persona obtenerPorId(Long id) {
        return personaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Persona no encontrada: " + id));
    }

    public Persona crear(Persona persona) {
        return personaRepository.save(persona);
    }

    public Persona actualizar(Long id, Persona datos) {
        Persona existente = obtenerPorId(id);
        existente.setNombre(datos.getNombre());
        existente.setEdad(datos.getEdad());
        existente.setSexo(datos.getSexo());
        existente.setPais(datos.getPais());
        existente.setCiudad(datos.getCiudad());
        existente.setEducacion(datos.getEducacion());
        existente.setOcupacion(datos.getOcupacion());
        existente.setPersonalidad(datos.getPersonalidad());
        existente.setNivelSocioeconomico(datos.getNivelSocioeconomico());
        return personaRepository.save(existente);
    }

    public void eliminar(Long id) {
        personaRepository.deleteById(id);
    }

    // ── Generar persona aleatoria con Claude y guardarla ──────────────────────
    public Persona generarAleatoria(String pais) throws Exception {
        String jsonRaw = claudeService.generarPersonaAleatoria(pais);

        // Limpiar posibles bloques markdown que Claude pudiera incluir
        String json = jsonRaw
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        JsonNode node = mapper.readTree(json);

        Persona persona = new Persona();
        persona.setNombre(node.path("nombre").asText());
        persona.setEdad(node.path("edad").asInt());
        persona.setSexo(node.path("sexo").asText());
        persona.setPais(node.path("pais").asText(pais));
        persona.setCiudad(node.path("ciudad").asText());
        persona.setEducacion(node.path("educacion").asText());
        persona.setOcupacion(node.path("ocupacion").asText());
        persona.setNivelSocioeconomico(node.path("nivelSocioeconomico").asText());
        persona.setPersonalidad(node.path("personalidad").asText());

        return personaRepository.save(persona);
    }

    public List<Persona> obtenerPorIds(List<Long> ids) {
        return personaRepository.findAllByIdIn(ids);
    }
}
