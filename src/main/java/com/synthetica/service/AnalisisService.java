/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.synthetica.service;

/**
 *
 * @author Totoralillo
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthetica.model.Respuesta;
import com.synthetica.repository.RespuestaRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalisisService {

    private final RespuestaRepository respuestaRepository;
    private final ClaudeService claudeService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalisisService(RespuestaRepository respuestaRepository, ClaudeService claudeService) {
        this.respuestaRepository = respuestaRepository;
        this.claudeService = claudeService;
    }

    public Map<String, Object> analizar(Long simulacionId) throws Exception {
        List<Respuesta> respuestas = respuestaRepository.findBySimulacionId(simulacionId);

        // Filtrar errores
        List<Respuesta> validas = respuestas.stream()
                .filter(r -> r.getRespuestaTexto() != null && !r.getRespuestaTexto().startsWith("[Error"))
                .toList();

        Map<String, Object> resultado = new LinkedHashMap<>();

        resultado.put("total", respuestas.size());
        resultado.put("totalValidas", validas.size());
        resultado.put("pregunta", respuestas.isEmpty() ? "" : respuestas.get(0).getPregunta().getTexto());

        // Distribución por sexo
        resultado.put("porSexo", distribucion(validas, r -> r.getPersona().getSexo()));

        // Distribución por NSE
        resultado.put("porNSE", distribucion(validas, r -> r.getPersona().getNivelSocioeconomico()));

        // Distribución por educación
        resultado.put("porEducacion", distribucion(validas, r -> r.getPersona().getEducacion()));

        // Distribución por rango de edad
        resultado.put("porEdad", distribucionEdad(validas));

        // Palabras frecuentes
        resultado.put("palabrasFrecuentes", palabrasFrecuentes(validas, 20));

        // Sentiment
        resultado.put("sentiment", analizarSentiment(validas));

        // Resumen con Claude
        resultado.put("resumen", generarResumen(validas));

        // Respuestas completas (primeras 20)
        resultado.put("respuestas", validas.stream().limit(20).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("persona", r.getPersona().getNombre() + ", "
                    + r.getPersona().getEdad() + " años, "
                    + r.getPersona().getCiudad() + ", "
                    + r.getPersona().getNivelSocioeconomico());
            m.put("texto", r.getRespuestaTexto());
            return m;
        }).toList());

        return resultado;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Map<String, Integer> distribucion(List<Respuesta> respuestas,
            java.util.function.Function<Respuesta, String> extractor) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (Respuesta r : respuestas) {
            String key = extractor.apply(r);
            if (key == null) {
                key = "No especificado";
            }
            dist.merge(key, 1, Integer::sum);
        }
        return dist.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    private Map<String, Integer> distribucionEdad(List<Respuesta> respuestas) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("18-24", 0);
        dist.put("25-34", 0);
        dist.put("35-44", 0);
        dist.put("45-54", 0);
        dist.put("55-64", 0);
        dist.put("65+", 0);

        for (Respuesta r : respuestas) {
            int edad = r.getPersona().getEdad();
            String rango = edad < 25 ? "18-24" : edad < 35 ? "25-34" : edad < 45 ? "35-44"
                    : edad < 55 ? "45-54" : edad < 65 ? "55-64" : "65+";
            dist.merge(rango, 1, Integer::sum);
        }
        return dist;
    }

    private List<Map<String, Object>> palabrasFrecuentes(List<Respuesta> respuestas, int top) {
        Set<String> stopwords = Set.of("que", "de", "en", "el", "la", "los", "las", "un", "una",
                "es", "por", "con", "no", "se", "si", "me", "te", "le", "su", "lo",
                "pero", "más", "para", "como", "muy", "hay", "son", "al", "del",
                "también", "esto", "esta", "cuando", "porque", "ser", "tiene", "todo",
                "sería", "así", "mi", "ya", "he", "era", "eso", "ante", "tener",
                "mira", "honestamente", "todas", "cualquier", "realmente", "dicho");

        Map<String, Integer> freq = new HashMap<>();
        for (Respuesta r : respuestas) {
            if (r.getRespuestaTexto() == null) {
                continue;
            }
            String[] palabras = r.getRespuestaTexto().toLowerCase()
                    .replaceAll("[^a-záéíóúüñ\\s]", "").split("\\s+");
            for (String p : palabras) {
                if (p.length() > 3 && !stopwords.contains(p)) {
                    freq.merge(p, 1, Integer::sum);
                }
            }
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(top)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("palabra", e.getKey());
                    m.put("frecuencia", e.getValue());
                    return m;
                })
                .toList();
    }

    private Map<String, Object> analizarSentiment(List<Respuesta> respuestas) throws Exception {
        if (respuestas.isEmpty()) {
            return Map.of("positivo", 0, "negativo", 0, "neutro", 0);
        }

        String textos = respuestas.stream()
                .limit(30)
                .map(r -> "- " + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Analiza el sentimiento de estas respuestas de encuesta.
                Clasifica cuántas son POSITIVO, NEGATIVO o NEUTRO.
                Responde SOLO con JSON sin markdown:
                {"positivo": N, "negativo": N, "neutro": N}
                
                Respuestas:
                """ + textos;

        String raw = claudeService.llamarClaude(prompt);
        raw = raw.replaceAll("```json|```", "").trim();

        try {
            return mapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of("positivo", 0, "negativo", 0, "neutro", 0);
        }
    }

    private List<String> generarResumen(List<Respuesta> respuestas) throws Exception {
        if (respuestas.isEmpty()) {
            return List.of("No hay respuestas para analizar.");  // ← List.of, no String
        }

        String textos = respuestas.stream()
                .limit(50)
                .map(r -> "- " + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Eres un analista de mercado experto. Analiza estas respuestas de encuesta y genera un resumen ejecutivo.
                
                Entrega exactamente 4 puntos clave en formato JSON sin markdown:
                {"puntos": ["punto 1", "punto 2", "punto 3", "punto 4"]}
                
                Los puntos deben ser concretos, accionables y reflejar patrones reales en las respuestas.
                No repitas información. Cada punto debe aportar algo distinto.
                
                Respuestas:
                """ + textos;

        String raw = claudeService.llamarClaude(prompt);
        raw = raw.replaceAll("```json|```", "").trim();

        try {
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            return (List<String>) parsed.get("puntos");
        } catch (Exception e) {
            return List.of("No se pudo generar el resumen.");
        }
    }
}
