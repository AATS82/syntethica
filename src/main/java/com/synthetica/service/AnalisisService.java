package com.synthetica.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthetica.model.Respuesta;
import com.synthetica.repository.RespuestaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalisisService {

    private static final Logger log = LoggerFactory.getLogger(AnalisisService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_SENTIMENT = 100;

    private final RespuestaRepository respuestaRepository;
    private final ClaudeService claudeService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalisisService(RespuestaRepository respuestaRepository, ClaudeService claudeService) {
        this.respuestaRepository = respuestaRepository;
        this.claudeService = claudeService;
    }

    @Transactional
    public Map<String, Object> analizar(Long simulacionId) throws Exception {
        log.info("[ANALISIS] Iniciando análisis para simulacionId={}", simulacionId);

        List<Respuesta> respuestas = respuestaRepository.findBySimulacionIdOrdenado(simulacionId);

        List<Respuesta> validas = respuestas.stream()
                .filter(r -> r.getRespuestaTexto() != null && !r.getRespuestaTexto().startsWith("[Error"))
                .toList();

        log.info("[ANALISIS] {} respuestas totales, {} válidas", respuestas.size(), validas.size());

        // Extraer pregunta y contexto (dentro de la transacción para evitar LazyInitializationException)
        String preguntaTexto = respuestas.isEmpty() ? "" : respuestas.get(0).getPregunta().getTexto();
        String contextoEncuesta = "";
        if (!respuestas.isEmpty()) {
            var encuesta = respuestas.get(0).getPregunta().getEncuesta();
            if (encuesta != null && encuesta.getContexto() != null) {
                contextoEncuesta = encuesta.getContexto();
            }
        }
        log.debug("[ANALISIS] Pregunta: '{}', Contexto: '{}'", preguntaTexto, contextoEncuesta);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("total", respuestas.size());
        resultado.put("totalValidas", validas.size());
        resultado.put("tasaError", calcularTasaError(respuestas));
        resultado.put("pregunta", preguntaTexto);

        // ── Distribuciones demográficas (se usan internamente en otros análisis) ─
        Map<String, Integer> porSexo = distribucion(validas, r -> r.getSexoEfectivo());
        Map<String, Integer> porNSE = distribucion(validas, r -> r.getNseEfectivo());
        Map<String, Integer> porEducacion = distribucion(validas, r -> r.getEducacionEfectiva());
        Map<String, Integer> porEdad = distribucionEdad(validas);

        resultado.put("porPais", distribucion(validas, r -> r.getPaisEfectivo()));
        resultado.put("porCiudad", top(distribucion(validas, r -> r.getCiudadEfectiva()), 10));
        resultado.put("porOcupacion", top(distribucion(validas, r -> r.getOcupacionEfectiva()), 10));

        // ── Métricas de engagement ────────────────────────────────────────────
        resultado.put("engagement", calcularEngagement(validas));

        // ── Palabras frecuentes ───────────────────────────────────────────────
        resultado.put("palabrasFrecuentes", palabrasFrecuentes(validas, 20));

        // ── Análisis con Claude ───────────────────────────────────────────────
        log.info("[ANALISIS] Generando análisis de sentiment...");
        resultado.put("sentiment", analizarSentiment(validas, preguntaTexto));

        log.info("[ANALISIS] Generando insights demográficos...");
        resultado.putAll(generarInsightsDemograficos(validas, preguntaTexto, porSexo, porNSE, porEdad, porEducacion));

        log.info("[ANALISIS] Generando temas y nivel de consenso...");
        resultado.putAll(analizarTemasYConsenso(validas, preguntaTexto));

        log.info("[ANALISIS] Generando citas destacadas y diferencias entre grupos...");
        resultado.putAll(analizarCitasYDiferencias(validas, preguntaTexto));

        log.info("[ANALISIS] Generando resumen ejecutivo ({} respuestas)...", validas.size());
        resultado.put("resumen", generarResumen(validas, preguntaTexto, contextoEncuesta, porSexo, porNSE, porEdad));

        resultado.put("respuestas", validas.stream().limit(20).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("persona", r.getPerfilDescripcion());
            m.put("texto", r.getRespuestaTexto());
            return m;
        }).toList());

        log.info("[ANALISIS] Análisis completado para simulacionId={}", simulacionId);
        return resultado;
    }

    // ── Resumen ejecutivo (map-reduce) ────────────────────────────────────────

    private List<String> generarResumen(List<Respuesta> respuestas, String pregunta, String contexto,
            Map<String, Integer> porSexo, Map<String, Integer> porNSE,
            Map<String, Integer> porEdad) throws Exception {
        if (respuestas.isEmpty()) {
            return List.of("No hay respuestas para analizar.");
        }

        String perfilDemografico = construirPerfilDemografico(respuestas.size(), porSexo, porNSE, porEdad);

        // Directo si cabe en un lote
        if (respuestas.size() <= BATCH_SIZE) {
            return resumirLote(respuestas, pregunta, contexto, perfilDemografico, 4);
        }

        // Map: resumir cada lote en 3 puntos parciales
        int totalLotes = (int) Math.ceil((double) respuestas.size() / BATCH_SIZE);
        log.info("[ANALISIS] Map-reduce: {} lotes de hasta {} respuestas", totalLotes, BATCH_SIZE);

        List<String> todosLosPuntos = new ArrayList<>();
        for (int i = 0; i < respuestas.size(); i += BATCH_SIZE) {
            List<Respuesta> lote = respuestas.subList(i, Math.min(i + BATCH_SIZE, respuestas.size()));
            int loteNum = (i / BATCH_SIZE) + 1;
            log.debug("[ANALISIS] Resumiendo lote {}/{}", loteNum, totalLotes);
            List<String> puntosParciales = resumirLote(lote, pregunta, contexto, perfilDemografico, 3);
            todosLosPuntos.addAll(puntosParciales);
        }

        // Reduce: sintetizar todos los puntos parciales en 4 definitivos
        log.debug("[ANALISIS] Sintetizando {} puntos parciales en resumen final", todosLosPuntos.size());
        return sintetizarPuntos(todosLosPuntos, pregunta, contexto, perfilDemografico);
    }

    private List<String> resumirLote(List<Respuesta> lote, String pregunta, String contexto,
            String perfilDemografico, int numPuntos) throws Exception {

        // Incluir datos demográficos por respuesta para que Claude detecte patrones por segmento
        String textos = lote.stream()
                .map(r -> "- [" + r.getSexoEfectivo() + ", " + r.getEdadEfectiva() + " años, "
                        + r.getNseEfectivo() + "] " + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String contextoStr = esContextoSignificativo(contexto) ? "\nCONTEXTO: " + contexto : "";

        String prompt = """
                Analiza estas respuestas de encuesta y explica los hallazgos principales.

                PREGUNTA: %s%s

                PERFIL DE RESPONDENTES:
                %s

                RESPUESTAS (%d respuestas, formato [sexo, edad, NSE] texto):
                %s

                Genera exactamente %d puntos clave. Reglas de escritura:
                - Usa lenguaje simple y directo, como si se lo explicaras a alguien sin conocimientos técnicos
                - Nada de jerga: no uses palabras como "segmento", "accionable", "hallazgo", "patrón", "cohorte"
                - Escribe oraciones cortas y concretas: "La mayoría de las mujeres jóvenes opina que..." en lugar de "Se evidencia una tendencia en el segmento femenino..."
                - Cuando haya diferencias entre grupos (hombres/mujeres, jóvenes/mayores, NSE alto/bajo), menciónalas en lenguaje natural
                - Cada punto debe decir algo diferente

                Responde SOLO con JSON sin markdown:
                {"puntos": ["punto 1", "punto 2", ...]}
                """.formatted(pregunta, contextoStr, perfilDemografico, lote.size(), textos, numPuntos);

        String raw = claudeService.llamarClaude(prompt);
        raw = raw.replaceAll("```json|```", "").trim();

        try {
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            return (List<String>) parsed.get("puntos");
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo parsear resumen de lote: {}", raw);
            return List.of("No se pudo analizar este bloque de respuestas.");
        }
    }

    private List<String> sintetizarPuntos(List<String> puntosParciales, String pregunta,
            String contexto, String perfilDemografico) throws Exception {

        String puntosStr = puntosParciales.stream()
                .map(p -> "- " + p)
                .collect(Collectors.joining("\n"));

        String contextoStr = esContextoSignificativo(contexto) ? "\nCONTEXTO: " + contexto : "";

        String prompt = """
                A partir de estos apuntes sobre una encuesta grande, escribe el resumen final en 4 puntos.

                PREGUNTA ORIGINAL: %s%s

                PERFIL DE RESPONDENTES:
                %s

                APUNTES PREVIOS (de distintos grupos de respuestas):
                %s

                Escribe exactamente 4 puntos para el resumen final. Reglas de escritura:
                - Lenguaje simple y directo, como si se lo explicaras a alguien sin conocimientos técnicos
                - Nada de jerga: no uses "segmento", "accionable", "hallazgo", "patrón", "cohorte"
                - Oraciones cortas y concretas
                - Elimina ideas repetidas, quédate con lo más importante
                - Cada punto debe decir algo diferente

                Responde SOLO con JSON sin markdown:
                {"puntos": ["punto 1", "punto 2", "punto 3", "punto 4"]}
                """.formatted(pregunta, contextoStr, perfilDemografico, puntosStr);

        String raw = claudeService.llamarClaude(prompt);
        raw = raw.replaceAll("```json|```", "").trim();

        try {
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            return (List<String>) parsed.get("puntos");
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo parsear síntesis final: {}", raw);
            return puntosParciales.stream().limit(4).toList();
        }
    }

    // ── Sentiment con muestra estratificada ───────────────────────────────────

    private Map<String, Object> analizarSentiment(List<Respuesta> respuestas, String pregunta) throws Exception {
        if (respuestas.isEmpty()) {
            return Map.of("positivo", 0, "negativo", 0, "neutro", 0);
        }

        List<Respuesta> muestra = tomarMuestraEstratificada(respuestas, MAX_SENTIMENT);
        log.debug("[ANALISIS] Sentiment sobre muestra de {}/{} respuestas", muestra.size(), respuestas.size());

        String textos = muestra.stream()
                .map(r -> "- " + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Analiza el sentimiento de estas respuestas a la pregunta: "%s"
                Clasifica cada una como POSITIVO, NEGATIVO o NEUTRO en relación a la pregunta.
                Estás analizando %d respuestas que representan %d respondentes en total.
                Responde SOLO con JSON sin markdown:
                {"positivo": N, "negativo": N, "neutro": N}

                Respuestas:
                %s
                """.formatted(pregunta, muestra.size(), respuestas.size(), textos);

        String raw = claudeService.llamarClaude(prompt);
        raw = raw.replaceAll("```json|```", "").trim();

        try {
            Map<String, Object> sentimentMuestra = mapper.readValue(raw, Map.class);

            // Escalar al total si se usó muestra parcial
            if (muestra.size() < respuestas.size()) {
                double factor = (double) respuestas.size() / muestra.size();
                Map<String, Object> escalado = new LinkedHashMap<>();
                escalado.put("positivo", (int) Math.round(((Number) sentimentMuestra.get("positivo")).doubleValue() * factor));
                escalado.put("negativo", (int) Math.round(((Number) sentimentMuestra.get("negativo")).doubleValue() * factor));
                escalado.put("neutro",   (int) Math.round(((Number) sentimentMuestra.get("neutro")).doubleValue() * factor));
                return escalado;
            }
            return sentimentMuestra;
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo parsear sentiment: {}", raw);
            return Map.of("positivo", 0, "negativo", 0, "neutro", 0);
        }
    }

    /**
     * Muestra proporcional por NSE para no sesgar el sentiment hacia un solo grupo.
     */
    private List<Respuesta> tomarMuestraEstratificada(List<Respuesta> respuestas, int maxMuestra) {
        if (respuestas.size() <= maxMuestra) {
            return respuestas;
        }

        Map<String, List<Respuesta>> porNSE = respuestas.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getNseEfectivo() != null ? r.getNseEfectivo() : "No especificado"));

        List<Respuesta> muestra = new ArrayList<>();
        for (Map.Entry<String, List<Respuesta>> entry : porNSE.entrySet()) {
            int cuota = Math.max(1, (int) Math.round(
                    (double) entry.getValue().size() / respuestas.size() * maxMuestra));
            muestra.addAll(entry.getValue().stream().limit(cuota).toList());
        }

        // Completar si el redondeo dejó huecos
        if (muestra.size() < maxMuestra) {
            Set<Long> yaIncluidos = muestra.stream().map(Respuesta::getId).collect(Collectors.toSet());
            respuestas.stream()
                    .filter(r -> !yaIncluidos.contains(r.getId()))
                    .limit(maxMuestra - muestra.size())
                    .forEach(muestra::add);
        }

        return muestra.stream().limit(maxMuestra).toList();
    }

    // ── Métricas sin costo ────────────────────────────────────────────────────

    private Map<String, Object> calcularTasaError(List<Respuesta> respuestas) {
        long errores = respuestas.stream()
                .filter(r -> r.getRespuestaTexto() == null || r.getRespuestaTexto().startsWith("[Error"))
                .count();
        double porcentaje = respuestas.isEmpty() ? 0.0
                : Math.round(100.0 * errores / respuestas.size() * 10) / 10.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cantidad", errores);
        m.put("porcentaje", porcentaje);
        return m;
    }

    private Map<String, Object> calcularEngagement(List<Respuesta> validas) {
        if (validas.isEmpty()) {
            return Map.of("cortas", 0, "medias", 0, "largas", 0, "promedioCaracteres", 0);
        }

        long cortas = 0, medias = 0, largas = 0, totalChars = 0;
        for (Respuesta r : validas) {
            int len = r.getRespuestaTexto().trim().length();
            totalChars += len;
            if (len < 80) cortas++;
            else if (len < 200) medias++;
            else largas++;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cortas", cortas);       // < 80 caracteres
        m.put("medias", medias);       // 80-200 caracteres
        m.put("largas", largas);       // > 200 caracteres
        m.put("promedioCaracteres", (int) (totalChars / validas.size()));
        return m;
    }

    /** Devuelve solo los primeros {@code n} entries del mapa (ya viene ordenado por valor desc). */
    private Map<String, Integer> top(Map<String, Integer> dist, int n) {
        return dist.entrySet().stream()
                .limit(n)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    // ── Insights demográficos enriquecidos (1 llamada Claude) ────────────────

    private Map<String, Object> generarInsightsDemograficos(List<Respuesta> validas, String pregunta,
            Map<String, Integer> porSexo, Map<String, Integer> porNSE,
            Map<String, Integer> porEdad, Map<String, Integer> porEducacion) {
        if (validas.isEmpty()) {
            return Map.of("porSexo", List.of(), "porNSE", List.of(),
                    "porEdad", List.of(), "porEducacion", List.of());
        }

        int total = validas.size();

        // Agrupar respuestas por cada dimensión con hasta 4 ejemplos por grupo
        String bloqueNSE    = construirBloqueGrupo("NSE",        validas, r -> r.getNseEfectivo(),        porNSE,       total, 4);
        String bloqueSexo   = construirBloqueGrupo("Sexo",       validas, r -> r.getSexoEfectivo(),       porSexo,      total, 4);
        String bloqueEdad   = construirBloqueGrupoEdad(validas, porEdad, total, 4);
        String bloqueEduc   = construirBloqueGrupo("Educación",  validas, r -> r.getEducacionEfectiva(),  porEducacion, total, 3);

        String prompt = """
                Tienes respuestas a esta pregunta de encuesta: "%s"

                Para cada grupo demográfico, escribe UNA frase corta y directa que resuma qué piensa ese grupo.
                La frase debe:
                - Decir algo concreto sobre su opinión (no solo "tienen opiniones variadas")
                - Usar lenguaje simple, sin jerga
                - Mencionar si hay algo llamativo o diferente en ese grupo

                %s

                %s

                %s

                %s

                Responde SOLO con JSON sin markdown. El formato exacto es:
                {
                  "porNSE":       [{"grupo":"...","cantidad":N,"porcentaje":N,"opinion":"..."}],
                  "porSexo":      [{"grupo":"...","cantidad":N,"porcentaje":N,"opinion":"..."}],
                  "porEdad":      [{"grupo":"...","cantidad":N,"porcentaje":N,"opinion":"..."}],
                  "porEducacion": [{"grupo":"...","cantidad":N,"porcentaje":N,"opinion":"..."}]
                }
                """.formatted(pregunta, bloqueNSE, bloqueSexo, bloqueEdad, bloqueEduc);

        try {
            String raw = claudeService.llamarClaude(prompt).replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);

            // Si Claude devuelve algo incompleto, rellenamos con la distribución básica
            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("porNSE",       parsed.getOrDefault("porNSE",       distribucionBasica(porNSE,      total)));
            resultado.put("porSexo",      parsed.getOrDefault("porSexo",      distribucionBasica(porSexo,     total)));
            resultado.put("porEdad",      parsed.getOrDefault("porEdad",      distribucionBasica(porEdad,     total)));
            resultado.put("porEducacion", parsed.getOrDefault("porEducacion", distribucionBasica(porEducacion, total)));
            return resultado;
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo generar insights demográficos: {}", e.getMessage());
            return Map.of(
                    "porNSE",       distribucionBasica(porNSE,       total),
                    "porSexo",      distribucionBasica(porSexo,      total),
                    "porEdad",      distribucionBasica(porEdad,      total),
                    "porEducacion", distribucionBasica(porEducacion, total));
        }
    }

    /** Arma el bloque de texto para una dimensión, con ejemplos de respuestas por grupo. */
    private String construirBloqueGrupo(String dimension, List<Respuesta> validas,
            java.util.function.Function<Respuesta, String> extractor,
            Map<String, Integer> distribucion, int total, int ejemplosPorGrupo) {

        Map<String, List<Respuesta>> agrupadas = validas.stream()
                .collect(Collectors.groupingBy(r -> {
                    String v = extractor.apply(r);
                    return v != null ? v : "No especificado";
                }));

        StringBuilder sb = new StringBuilder("--- ").append(dimension).append(" ---\n");
        for (Map.Entry<String, Integer> entry : distribucion.entrySet()) {
            String grupo = entry.getKey();
            int cantidad = entry.getValue();
            int pct = Math.round(100.0f * cantidad / total);
            List<Respuesta> delGrupo = agrupadas.getOrDefault(grupo, List.of());

            sb.append(grupo).append(" (").append(pct).append("%, ").append(cantidad).append(" personas):\n");
            delGrupo.stream().limit(ejemplosPorGrupo)
                    .forEach(r -> sb.append("  • ").append(r.getRespuestaTexto()).append("\n"));
        }
        return sb.toString();
    }

    /** Igual que construirBloqueGrupo pero para rangos de edad. */
    private String construirBloqueGrupoEdad(List<Respuesta> validas, Map<String, Integer> porEdad,
            int total, int ejemplosPorGrupo) {

        Map<String, List<Respuesta>> agrupadas = validas.stream()
                .collect(Collectors.groupingBy(r -> {
                    Integer edad = r.getEdadEfectiva();
                    if (edad == null) return "No especificado";
                    return edad < 25 ? "18-24" : edad < 35 ? "25-34" : edad < 45 ? "35-44"
                            : edad < 55 ? "45-54" : edad < 65 ? "55-64" : "65+";
                }));

        StringBuilder sb = new StringBuilder("--- Edad ---\n");
        for (Map.Entry<String, Integer> entry : porEdad.entrySet()) {
            if (entry.getValue() == 0) continue;
            String grupo = entry.getKey();
            int cantidad = entry.getValue();
            int pct = Math.round(100.0f * cantidad / total);
            List<Respuesta> delGrupo = agrupadas.getOrDefault(grupo, List.of());

            sb.append(grupo).append(" años (").append(pct).append("%, ").append(cantidad).append(" personas):\n");
            delGrupo.stream().limit(ejemplosPorGrupo)
                    .forEach(r -> sb.append("  • ").append(r.getRespuestaTexto()).append("\n"));
        }
        return sb.toString();
    }

    /** Fallback: si Claude falla, devuelve la distribución sin insights. */
    private List<Map<String, Object>> distribucionBasica(Map<String, Integer> dist, int total) {
        return dist.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("grupo", e.getKey());
                    m.put("cantidad", e.getValue());
                    m.put("porcentaje", Math.round(100.0f * e.getValue() / total));
                    m.put("opinion", "");
                    return m;
                })
                .toList();
    }

    // ── Análisis temático + consenso (1 llamada Claude) ───────────────────────

    private Map<String, Object> analizarTemasYConsenso(List<Respuesta> validas, String pregunta) {
        if (validas.isEmpty()) {
            return Map.of(
                    "temasPrincipales", List.of(),
                    "nivelConsenso", Map.of("nivel", "sin datos", "descripcion", "No hay respuestas."));
        }

        List<Respuesta> muestra = tomarMuestraEstratificada(validas, BATCH_SIZE);

        String textos = muestra.stream()
                .map(r -> "- [" + r.getSexoEfectivo() + ", " + r.getEdadEfectiva()
                        + " años, " + r.getNseEfectivo() + "] "
                        + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Lee estas respuestas a la pregunta: "%s"

                RESPUESTAS (%d, formato [sexo, edad, NSE] texto):
                %s

                Necesito dos cosas:

                1. TEMAS: Agrupa las respuestas en los 4 o 5 temas principales que aparecen.
                   Para cada tema indica:
                   - "nombre": nombre corto y claro del tema (ej: "Preocupación por el precio")
                   - "porcentaje": qué porcentaje aproximado de respuestas toca este tema
                   - "resumen": una frase simple que explique qué piensa este grupo
                   - "ejemplos": 2 frases textuales cortas de las respuestas que ilustren el tema

                2. CONSENSO: ¿Qué tan de acuerdo están las personas entre sí?
                   - "nivel": "alto" si la mayoría piensa parecido, "medio" si hay posiciones distintas, "bajo" si las opiniones están muy divididas
                   - "descripcion": una oración simple explicando el nivel de consenso (sin jerga)

                Responde SOLO con JSON sin markdown:
                {
                  "temas": [{"nombre":"...","porcentaje":N,"resumen":"...","ejemplos":["...","..."]}],
                  "consenso": {"nivel":"...","descripcion":"..."}
                }
                """.formatted(pregunta, muestra.size(), textos);

        try {
            String raw = claudeService.llamarClaude(prompt).replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            return Map.of(
                    "temasPrincipales", parsed.getOrDefault("temas", List.of()),
                    "nivelConsenso", parsed.getOrDefault("consenso",
                            Map.of("nivel", "sin datos", "descripcion", "")));
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo parsear temas/consenso: {}", e.getMessage());
            return Map.of("temasPrincipales", List.of(), "nivelConsenso", Map.of());
        }
    }

    // ── Citas destacadas + diferencias entre grupos (1 llamada Claude) ─────────

    private Map<String, Object> analizarCitasYDiferencias(List<Respuesta> validas, String pregunta) {
        if (validas.isEmpty()) {
            return Map.of("citasDestacadas", List.of(), "diferenciasGrupos", List.of());
        }

        // Muestra diversa: hasta 8 por cada nivel NSE para garantizar representación
        List<Respuesta> muestra = tomarMuestraDiversa(validas, 40);

        String textos = muestra.stream()
                .map(r -> "- [" + r.getNombreEfectivo() + ", "
                        + r.getSexoEfectivo() + ", "
                        + r.getEdadEfectiva() + " años, "
                        + r.getOcupacionEfectiva() + ", "
                        + r.getNseEfectivo() + "] "
                        + r.getRespuestaTexto())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Lee estas respuestas a la pregunta: "%s"

                RESPUESTAS (%d, formato [nombre, sexo, edad, ocupación, NSE] texto):
                %s

                Necesito dos cosas:

                1. CITAS DESTACADAS: Elige 5 respuestas que sean especialmente interesantes, representativas o llamativas.
                   Para cada una indica:
                   - "texto": la respuesta textual (puedes recortarla si es muy larga)
                   - "perfil": descripción breve de la persona (ej: "Mujer de 34 años, NSE Medio")
                   - "razon": en una frase, por qué esta cita es relevante (en lenguaje simple)

                2. DIFERENCIAS ENTRE GRUPOS: ¿Qué grupos piensan diferente? Menciona 3 diferencias concretas
                   que notes entre hombres y mujeres, jóvenes y mayores, o entre niveles socioeconómicos.
                   Escríbelas en lenguaje simple, sin jerga.

                Responde SOLO con JSON sin markdown:
                {
                  "citasDestacadas": [{"texto":"...","perfil":"...","razon":"..."}],
                  "diferenciasGrupos": ["diferencia 1","diferencia 2","diferencia 3"]
                }
                """.formatted(pregunta, muestra.size(), textos);

        try {
            String raw = claudeService.llamarClaude(prompt).replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = mapper.readValue(raw, Map.class);
            return Map.of(
                    "citasDestacadas", parsed.getOrDefault("citasDestacadas", List.of()),
                    "diferenciasGrupos", parsed.getOrDefault("diferenciasGrupos", List.of()));
        } catch (Exception e) {
            log.warn("[ANALISIS] No se pudo parsear citas/diferencias: {}", e.getMessage());
            return Map.of("citasDestacadas", List.of(), "diferenciasGrupos", List.of());
        }
    }

    /**
     * Muestra diversa: proporcional por NSE Y por sexo, para garantizar variedad de perspectivas.
     */
    private List<Respuesta> tomarMuestraDiversa(List<Respuesta> respuestas, int maxMuestra) {
        if (respuestas.size() <= maxMuestra) {
            return respuestas;
        }

        Map<String, List<Respuesta>> porGrupo = respuestas.stream()
                .collect(Collectors.groupingBy(r -> {
                    String nse  = r.getNseEfectivo()  != null ? r.getNseEfectivo()  : "No especificado";
                    String sexo = r.getSexoEfectivo() != null ? r.getSexoEfectivo() : "No especificado";
                    return nse + "|" + sexo;
                }));

        List<Respuesta> muestra = new ArrayList<>();
        int cuotaPorGrupo = Math.max(1, maxMuestra / porGrupo.size());

        for (List<Respuesta> grupo : porGrupo.values()) {
            muestra.addAll(grupo.stream().limit(cuotaPorGrupo).toList());
        }

        // Completar huecos por redondeo
        if (muestra.size() < maxMuestra) {
            Set<Long> yaIncluidos = muestra.stream().map(Respuesta::getId).collect(Collectors.toSet());
            respuestas.stream()
                    .filter(r -> !yaIncluidos.contains(r.getId()))
                    .limit(maxMuestra - muestra.size())
                    .forEach(muestra::add);
        }

        return muestra.stream().limit(maxMuestra).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String construirPerfilDemografico(int total, Map<String, Integer> porSexo,
            Map<String, Integer> porNSE, Map<String, Integer> porEdad) {
        return "Total: " + total + " respondentes\n"
                + "Sexo: " + formatearDistribucion(porSexo, total) + "\n"
                + "NSE: " + formatearDistribucion(porNSE, total) + "\n"
                + "Edad: " + formatearDistribucion(porEdad, total);
    }

    private String formatearDistribucion(Map<String, Integer> dist, int total) {
        return dist.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey() + " " + Math.round(100.0 * e.getValue() / total) + "%")
                .collect(Collectors.joining(", "));
    }

    private boolean esContextoSignificativo(String contexto) {
        return contexto != null && !contexto.isBlank()
                && !contexto.equals("Encuesta rápida generada automáticamente");
    }

    private Map<String, Integer> distribucion(List<Respuesta> respuestas,
            java.util.function.Function<Respuesta, String> extractor) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (Respuesta r : respuestas) {
            String key = extractor.apply(r);
            if (key == null) key = "No especificado";
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
            Integer edad = r.getEdadEfectiva();
            if (edad == null) continue;
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
            if (r.getRespuestaTexto() == null) continue;
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
}
