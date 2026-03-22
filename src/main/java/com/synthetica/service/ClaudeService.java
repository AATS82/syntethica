package com.synthetica.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthetica.model.Persona;
import com.synthetica.model.Pregunta;
import com.synthetica.model.enums.TipoPregunta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private int maxTokens;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Responder una pregunta como una persona ───────────────────────────────
    public String responderPregunta(Persona persona, Pregunta pregunta, String contextoEncuesta) throws Exception {

        String systemPrompt = buildSystemPrompt(persona, contextoEncuesta, pregunta.getTipo());
        String userPrompt   = buildUserPrompt(pregunta);

        Map<String, Object> body = Map.of(
            "model",      model,
            "max_tokens", maxTokens,
            "system",     systemPrompt,
            "messages",   List.of(Map.of("role", "user", "content", userPrompt))
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        return json.path("content").get(0).path("text").asText().trim();
    }

    // ── Generar una persona aleatoria ─────────────────────────────────────────
    public String generarPersonaAleatoria(String pais) throws Exception {

        String prompt = String.format("""
            Genera una persona ficticia y realista de %s para usar como respondente de encuestas.
            
            Responde SOLO con JSON, sin markdown, sin explicaciones. Formato exacto:
            {
              "nombre": "...",
              "edad": 00,
              "sexo": "Masculino|Femenino",
              "pais": "%s",
              "ciudad": "...",
              "educacion": "Básica|Media|Técnica|Universitaria|Postgrado",
              "ocupacion": "...",
              "nivelSocioeconomico": "Bajo|Medio-bajo|Medio|Medio-alto|Alto",
              "personalidad": "descripción breve de 1-2 rasgos relevantes para consumo y opinión"
            }
            
            Hazla diversa y realista. Varía clase social, ocupación y características demográficas.
            """, pais, pais);

        Map<String, Object> body = Map.of(
            "model",      model,
            "max_tokens", 512,
            "messages",   List.of(Map.of("role", "user", "content", prompt))
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error al generar persona: " + response.statusCode());
        }

        JsonNode json = mapper.readTree(response.body());
        return json.path("content").get(0).path("text").asText().trim();
    }

    // ── Builders de prompts ───────────────────────────────────────────────────

    private String buildSystemPrompt(Persona persona, String contextoEncuesta, TipoPregunta tipo) {
        StringBuilder sb = new StringBuilder();

        sb.append(persona.toPromptContext());
        sb.append("\n\n");
        sb.append("Estás participando en una encuesta. ");

        if (contextoEncuesta != null && !contextoEncuesta.isBlank()) {
            sb.append("Contexto de la encuesta: ").append(contextoEncuesta).append("\n\n");
        }

        sb.append("Responde de forma auténtica y coherente con tu perfil demográfico, ");
        sb.append("tu nivel educacional, tu situación económica y tus rasgos de personalidad. ");
        sb.append("Habla en primera persona, como lo haría una persona real en una encuesta.");

        if (tipo == TipoPregunta.LIKERT) {
            sb.append("\n\nIMPORTANTE: La pregunta es de escala Likert 1-5. ");
            sb.append("Responde SOLO con el número (1, 2, 3, 4 o 5) seguido de una breve justificación de máximo 2 oraciones. ");
            sb.append("Formato: \"[número]: [justificación]\"");
        } else {
            sb.append("\n\nResponde de forma natural y directa. Máximo 3-4 oraciones.");
        }

        return sb.toString();
    }

    private String buildUserPrompt(Pregunta pregunta) {
        if (pregunta.getTipo() == TipoPregunta.LIKERT) {
            return pregunta.getTexto() + "\n\n(Escala: 1=Totalmente en desacuerdo, 2=En desacuerdo, 3=Neutral, 4=De acuerdo, 5=Totalmente de acuerdo)";
        }
        return pregunta.getTexto();
    }
}
