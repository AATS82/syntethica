package com.synthetica.service;

import com.synthetica.dto.EncuestaRapidaRequestDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class PoblacionService {

    // ── Nombres ───────────────────────────────────────────────────────────────
    private static final String[] NOMBRES_FEMENINOS = {
        "María", "Carolina", "Ana", "Isabel", "Claudia", "Valentina", "Camila", "Daniela",
        "Andrea", "Patricia", "Gabriela", "Francisca", "Javiera", "Catalina", "Fernanda",
        "Nicole", "Paola", "Sofía", "Lorena", "Sandra", "Cecilia", "Verónica", "Mónica",
        "Pilar", "Alejandra", "Paula", "Gloria", "Rosa", "Carmen", "Teresa", "Natalia",
        "Marcela", "Ximena", "Constanza", "Josefina", "Ignacia", "Antonia", "Isidora",
        "Agustina", "Florencia", "Macarena", "Tamara", "Diana", "Paloma", "Rebeca",
        "Amanda", "Pía", "Carla", "Viviana", "Beatriz"
    };

    private static final String[] NOMBRES_MASCULINOS = {
        "Juan", "Carlos", "José", "Luis", "Miguel", "Pablo", "Rodrigo", "Ricardo",
        "Andrés", "Felipe", "Diego", "Sebastián", "Gabriel", "Francisco", "Eduardo",
        "Alejandro", "Pedro", "Cristóbal", "Tomás", "Nicolás", "Matías", "Jorge",
        "Mario", "Roberto", "Claudio", "Fernando", "Patricio", "Sergio", "Manuel",
        "Héctor", "Raúl", "Javier", "Daniel", "Ignacio", "Marcelo", "Gonzalo",
        "Álvaro", "Cristian", "Esteban", "Jaime", "René", "Agustín", "Benjamín",
        "Alonso", "Vicente", "Emilio", "Enrique", "Gustavo", "Alberto", "Hugo"
    };

    private static final String[] APELLIDOS = {
        "González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras", "Silva",
        "Martínez", "Sepúlveda", "Morales", "Torres", "Fernández", "Flores", "López",
        "Ramírez", "Pizarro", "Castro", "Vega", "Castillo", "Rivera", "Arenas",
        "Fuentes", "Herrera", "Medina", "Vargas", "Valenzuela", "Cortés", "Sanhueza",
        "Gutiérrez", "Reyes", "Espinoza", "Salazar", "Bravo", "Álvarez", "Vera",
        "Aguilera", "Figueroa", "Palma", "Núñez", "Riquelme", "Miranda", "Urrutia",
        "Lara", "Henríquez", "Carrasco", "Cabrera", "Jiménez", "Troncoso", "Leiva"
    };

    // ── Regiones y ciudades — Censo 2024 ─────────────────────────────────────
    private static final String[] REGIONES = {
        "Región Metropolitana", "Valparaíso", "Biobío", "La Araucanía", "Maule",
        "O'Higgins", "Los Lagos", "Antofagasta", "Coquimbo", "Los Ríos",
        "Tarapacá", "Ñuble", "Atacama", "Arica y Parinacota", "Aysén", "Magallanes"
    };

    private static final double[] PESOS_REGION = {
        0.402, 0.096, 0.087, 0.053, 0.052, 0.050, 0.044, 0.038, 0.038, 0.025,
        0.019, 0.018, 0.017, 0.013, 0.006, 0.006
    };

    private static final String[][] CIUDADES_POR_REGION = {
        {"Santiago", "Puente Alto", "Maipú", "La Florida", "Las Condes", "Providencia", "Ñuñoa", "San Bernardo"},
        {"Valparaíso", "Viña del Mar", "Quilpué", "Villa Alemana", "San Antonio"},
        {"Concepción", "Talcahuano", "Los Ángeles", "Coronel", "Chiguayante"},
        {"Temuco", "Padre Las Casas", "Angol", "Villarrica"},
        {"Talca", "Curicó", "Linares", "Constitución"},
        {"Rancagua", "San Fernando", "Rengo", "Machalí"},
        {"Puerto Montt", "Osorno", "Castro", "Puerto Varas"},
        {"Antofagasta", "Calama", "Tocopilla"},
        {"La Serena", "Coquimbo", "Ovalle", "Illapel"},
        {"Valdivia", "La Unión", "Los Lagos"},
        {"Iquique", "Alto Hospicio"},
        {"Chillán", "San Carlos", "Bulnes"},
        {"Copiapó", "Vallenar", "Chañaral"},
        {"Arica"},
        {"Coyhaique", "Puerto Aysén"},
        {"Punta Arenas", "Puerto Natales"}
    };

    // ── Edad — INE 2024 (18+) ────────────────────────────────────────────────
    private static final int[] EDAD_MIN_BUCKET = {18, 25, 35, 45, 55, 65, 76};
    private static final int[] EDAD_MAX_BUCKET = {24, 34, 44, 54, 64, 75, 90};
    private static final double[] PESOS_EDAD = {0.115, 0.195, 0.185, 0.170, 0.155, 0.115, 0.065};

    // ── NSE — Quintiles CASEN 2022 ────────────────────────────────────────────
    private static final String[] NSE = {"Bajo", "Medio-bajo", "Medio", "Medio-alto", "Alto"};
    private static final double[] PESOS_NSE = {0.20, 0.20, 0.20, 0.20, 0.20};

    // ── Educación — CASEN 2022 ────────────────────────────────────────────────
    private static final String[] EDUCACION = {"Básica", "Media", "Técnica", "Universitaria", "Postgrado"};
    private static final double[] PESOS_EDUCACION_BASE = {0.160, 0.390, 0.210, 0.158, 0.052};

    // ── Situación laboral — INE 2024 ─────────────────────────────────────────
    private static final String[] SITUACION_LABORAL = {
        "Empleado/a sector privado", "Empleado/a sector público",
        "Trabajador/a independiente", "Emprendedor/a", "Trabajador/a informal",
        "Desempleado/a", "Jubilado/a", "Dueño/a de casa", "Estudiante"
    };
    private static final double[] PESOS_LABORAL = {
        0.280, 0.085, 0.145, 0.065, 0.085, 0.082, 0.138, 0.065, 0.055
    };

    // ── Religión — CEP 2023 ──────────────────────────────────────────────────
    private static final String[] RELIGION = {"Católico/a", "Evangélico/a", "Sin religión/Agnóstico", "Ateo/a", "Otra"};
    private static final double[] PESOS_RELIGION = {0.425, 0.178, 0.300, 0.062, 0.035};

    // ── Previsión salud — CASEN 2022 ──────────────────────────────────────────
    private static final String[] PREVISION = {"Fonasa", "Isapre", "Sin previsión", "Otra"};

    // ── Atributos psicográficos ───────────────────────────────────────────────
    private static final String[] PERSONALIDADES = {
        "pragmática", "empática", "desconfiada del sistema", "optimista",
        "tradicional", "crítica social", "consumista", "austera", "tecnológica", "conservadora"
    };

    private static final String[] SENSIBILIDAD_PRECIO = {
        "muy sensible al precio", "compara precios antes de comprar",
        "prioriza calidad sobre precio", "paga premium por buenas marcas",
        "valora el equilibrio precio-calidad"
    };

    private static final String[] ADOPCION_TECNOLOGICA = {
        "early adopter", "adoptador temprano", "mayoría temprana",
        "mayoría tardía", "rezagado tecnológico"
    };

    private static final String[] REDES_SOCIALES = {
        "activo/a en Instagram y TikTok", "usuario/a principalmente de Facebook",
        "solo usa WhatsApp", "consumidor/a de YouTube", "profesional en LinkedIn",
        "minimalista digital"
    };

    private static final String[] TRANSPORTE = {
        "auto propio", "transporte público", "Uber/Bolt frecuente",
        "combinación de medios", "bicicleta o caminata"
    };

    private static final String[] POSTURA_POLITICA = {
        "derecha", "centro derecha", "centro", "centro izquierda",
        "izquierda", "desinteresado/a de la política", "antipolítico/a"
    };

    private static final String[] LEALTAD_MARCA = {
        "muy leal a marcas conocidas", "leal a pocas marcas de confianza",
        "prefiere productos nacionales", "decide siempre por precio",
        "abierto/a a probar marcas nuevas"
    };

    private static final String[] ALIMENTACION = {
        "omnívoro/a", "flexitariano/a", "vegetariano/a", "vegano/a",
        "dieta especial por salud", "fast food frecuente"
    };

    private static final String[] BANCO = {
        "Banco Estado", "banco privado grande (Santander/BCI/Chile)",
        "cuenta RUT básica", "billetera digital (Mercado Pago/Tenpo)",
        "desbancarizado/a"
    };

    private static final String[] ENDEUDAMIENTO = {
        "usa crédito responsablemente", "deudas de consumo vigentes",
        "crédito hipotecario activo", "sobreendeudado/a", "evita todo crédito"
    };

    // ═════════════════════════════════════════════════════════════════════════
    // Método principal
    // ═════════════════════════════════════════════════════════════════════════
    public List<PerfilSintetico> samplearPerfiles(EncuestaRapidaRequestDTO.FiltrosDTO filtros, int cantidad) {
        Random rng = new Random();
        List<PerfilSintetico> perfiles = new ArrayList<>(cantidad);

        String sexoFiltro = filtros != null ? filtros.getSexoFiltro() : null;
        Integer edadMin = filtros != null ? filtros.getEdadMin() : null;
        Integer edadMax = filtros != null ? filtros.getEdadMax() : null;
        String educacionFiltro = filtros != null ? filtros.getEducacion() : null;
        String nseFiltro = filtros != null ? filtros.getNivelSocioeconomico() : null;

        for (int i = 0; i < cantidad; i++) {
            perfiles.add(generarPerfil(rng, sexoFiltro, edadMin, edadMax, educacionFiltro, nseFiltro));
        }
        return perfiles;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Generación de un perfil individual
    // ═════════════════════════════════════════════════════════════════════════
    private PerfilSintetico generarPerfil(Random rng,
            String sexoFiltro, Integer edadMin, Integer edadMax,
            String educacionFiltro, String nseFiltro) {

        // 1. Sexo
        String sexo = (sexoFiltro != null && !sexoFiltro.isBlank())
                ? sexoFiltro
                : (rng.nextDouble() < 0.515 ? "Femenino" : "Masculino");

        // 2. NSE
        String nse = (nseFiltro != null && !nseFiltro.isBlank())
                ? nseFiltro
                : samplearConPesos(NSE, PESOS_NSE, rng);

        // 3. Edad (con filtro de rango)
        int edad = samplearEdad(rng, edadMin, edadMax);

        // Correlación: joven <= 28 + NSE Alto → bajar a Medio-alto 60% de veces
        if (edad <= 28 && "Alto".equals(nse) && nseFiltro == null && rng.nextDouble() < 0.60) {
            nse = "Medio-alto";
        }

        // 4. Región → zona → ciudad
        int regionIdx = samplearIndiceConPesos(PESOS_REGION, rng);
        String region = REGIONES[regionIdx];
        boolean rural = rng.nextDouble() >= 0.878;
        String zona = rural ? "Rural" : "Urbana";
        String ciudad = rural
                ? "zona rural de " + region
                : CIUDADES_POR_REGION[regionIdx][rng.nextInt(CIUDADES_POR_REGION[regionIdx].length)];

        // 5. Educación (con correlaciones NSE y edad)
        String educacion = (educacionFiltro != null && !educacionFiltro.isBlank())
                ? educacionFiltro
                : samplearEducacion(rng, edad, nse);

        // Correlación: Postgrado solo en NSE Medio-alto/Alto con 80% de prob.
        if ("Postgrado".equals(educacion) && educacionFiltro == null
                && !"Medio-alto".equals(nse) && !"Alto".equals(nse)
                && rng.nextDouble() < 0.80) {
            educacion = "Universitaria";
        }

        // 6. Situación laboral y título de ocupación
        String situacionLaboral = samplearSituacionLaboral(rng, edad);
        String ocupacion = generarTituloOcupacion(situacionLaboral, educacion, rng);

        // 7. Previsión de salud (correlacionada con NSE)
        String prevision = samplearPrevision(rng, nse);

        // 8. Religión
        String religion = samplearConPesos(RELIGION, PESOS_RELIGION, rng);

        // 9. Psicográficos
        String personalidad = PERSONALIDADES[rng.nextInt(PERSONALIDADES.length)];
        String sensibilidadPrecio = SENSIBILIDAD_PRECIO[rng.nextInt(SENSIBILIDAD_PRECIO.length)];
        String adopcionTec = ADOPCION_TECNOLOGICA[rng.nextInt(ADOPCION_TECNOLOGICA.length)];
        String redes = REDES_SOCIALES[rng.nextInt(REDES_SOCIALES.length)];
        String transporte = samplearTransporte(rng, nse, zona);
        String posturaPolitica = POSTURA_POLITICA[rng.nextInt(POSTURA_POLITICA.length)];
        String lealtadMarca = LEALTAD_MARCA[rng.nextInt(LEALTAD_MARCA.length)];
        String alimentacion = ALIMENTACION[rng.nextInt(ALIMENTACION.length)];
        String banco = samplearBanco(rng, nse);
        String endeudamiento = ENDEUDAMIENTO[rng.nextInt(ENDEUDAMIENTO.length)];

        // 10. Nombre chileno
        String nombre = generarNombre(rng, sexo);

        return new PerfilSintetico(
                nombre, edad, sexo, "Chile", region, ciudad, zona,
                educacion, ocupacion, nse, prevision, religion,
                personalidad, sensibilidadPrecio, adopcionTec, redes,
                transporte, posturaPolitica, lealtadMarca, alimentacion, banco, endeudamiento
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    private String samplearEducacion(Random rng, int edad, String nse) {
        double[] p = PESOS_EDUCACION_BASE.clone();

        // Correlación: edad >= 65 → universitaria/postgrado menos probable
        if (edad >= 65) {
            p[3] *= 0.60;
            p[4] *= 0.60;
        }
        // Correlación NSE bajo → menos educación superior
        if ("Bajo".equals(nse)) {
            p[3] *= 0.20;
            p[4] *= 0.05;
        } else if ("Medio-bajo".equals(nse)) {
            p[3] *= 0.40;
            p[4] *= 0.15;
        }
        // Correlación NSE alto → más educación superior
        if ("Alto".equals(nse)) {
            p[0] *= 0.10;
            p[1] *= 0.25;
            p[3] *= 2.5;
            p[4] *= 4.0;
        } else if ("Medio-alto".equals(nse)) {
            p[0] *= 0.20;
            p[1] *= 0.50;
            p[3] *= 2.0;
            p[4] *= 2.0;
        }
        return samplearConPesos(EDUCACION, p, rng);
    }

    private String samplearSituacionLaboral(Random rng, int edad) {
        // Correlación: edad >= 65 → Jubilado/a muy probable
        if (edad >= 65) {
            double[] p = {0.05, 0.02, 0.05, 0.02, 0.03, 0.05, 0.60, 0.15, 0.00};
            return samplearConPesos(SITUACION_LABORAL, p, rng);
        }
        // Correlación: edad <= 24 → Estudiante muy probable
        if (edad <= 24) {
            double[] p = {0.10, 0.03, 0.05, 0.03, 0.08, 0.10, 0.00, 0.09, 0.40};
            return samplearConPesos(SITUACION_LABORAL, p, rng);
        }
        return samplearConPesos(SITUACION_LABORAL, PESOS_LABORAL, rng);
    }

    private String samplearPrevision(Random rng, String nse) {
        double[] p;
        switch (nse) {
            case "Bajo":
                p = new double[]{0.950, 0.020, 0.025, 0.005};
                break;
            case "Medio-bajo":
                p = new double[]{0.900, 0.060, 0.030, 0.010};
                break;
            case "Medio":
                p = new double[]{0.820, 0.140, 0.030, 0.010};
                break;
            case "Medio-alto":
                p = new double[]{0.600, 0.360, 0.025, 0.015};
                break;
            default:
                p = new double[]{0.450, 0.500, 0.025, 0.025}; // Alto
        }
        return samplearConPesos(PREVISION, p, rng);
    }

    private String samplearTransporte(Random rng, String nse, String zona) {
        if ("Rural".equals(zona)) {
            double[] p = {0.45, 0.30, 0.05, 0.18, 0.02};
            return samplearConPesos(TRANSPORTE, p, rng);
        }
        switch (nse) {
            case "Alto": {
                double[] p = {0.70, 0.05, 0.15, 0.07, 0.03};
                return samplearConPesos(TRANSPORTE, p, rng);
            }
            case "Medio-alto": {
                double[] p = {0.50, 0.15, 0.20, 0.10, 0.05};
                return samplearConPesos(TRANSPORTE, p, rng);
            }
            case "Bajo": {
                double[] p = {0.08, 0.58, 0.08, 0.21, 0.05};
                return samplearConPesos(TRANSPORTE, p, rng);
            }
            case "Medio-bajo": {
                double[] p = {0.15, 0.50, 0.10, 0.20, 0.05};
                return samplearConPesos(TRANSPORTE, p, rng);
            }
            default: {
                double[] p = {0.25, 0.35, 0.18, 0.15, 0.07};
                return samplearConPesos(TRANSPORTE, p, rng);
            }
        }
    }

    private String samplearBanco(Random rng, String nse) {
        switch (nse) {
            case "Bajo": {
                double[] p = {0.50, 0.05, 0.25, 0.10, 0.10};
                return samplearConPesos(BANCO, p, rng);
            }
            case "Medio-bajo": {
                double[] p = {0.45, 0.10, 0.20, 0.15, 0.10};
                return samplearConPesos(BANCO, p, rng);
            }
            case "Medio": {
                double[] p = {0.35, 0.30, 0.15, 0.15, 0.05};
                return samplearConPesos(BANCO, p, rng);
            }
            case "Medio-alto": {
                double[] p = {0.20, 0.55, 0.05, 0.15, 0.05};
                return samplearConPesos(BANCO, p, rng);
            }
            default: {
                double[] p = {0.10, 0.75, 0.02, 0.10, 0.03};
                return samplearConPesos(BANCO, p, rng);
            } // Alto
        }
    }

    private String generarTituloOcupacion(String situacion, String educacion, Random rng) {
        switch (situacion) {
            case "Empleado/a sector público": {
                String[] ops = {"Funcionario/a municipal", "Administrativo/a de gobierno",
                    "Técnico/a de servicio público", "Empleado/a público/a"};
                return ops[rng.nextInt(ops.length)];
            }
            case "Empleado/a sector privado": {
                if ("Universitaria".equals(educacion) || "Postgrado".equals(educacion)) {
                    String[] ops = {"Ingeniero/a", "Contador/a", "Administrador/a de empresas",
                        "Analista", "Ejecutivo/a comercial", "Profesional"};
                    return ops[rng.nextInt(ops.length)];
                } else if ("Técnica".equals(educacion)) {
                    String[] ops = {"Técnico/a", "Vendedor/a", "Supervisor/a de turno", "Operador/a"};
                    return ops[rng.nextInt(ops.length)];
                } else {
                    String[] ops = {"Operario/a de producción", "Cajero/a", "Vendedor/a de comercio", "Guardia"};
                    return ops[rng.nextInt(ops.length)];
                }
            }
            case "Trabajador/a independiente": {
                String[] ops = {"Consultor/a independiente", "Profesional freelance",
                    "Técnico/a independiente", "Prestador/a de servicios"};
                return ops[rng.nextInt(ops.length)];
            }
            case "Emprendedor/a": {
                String[] ops = {"Emprendedor/a", "Dueño/a de pequeño negocio", "Microempresario/a"};
                return ops[rng.nextInt(ops.length)];
            }
            case "Trabajador/a informal": {
                String[] ops = {"Feriante", "Vendedor/a ambulante", "Cuidador/a", "Trabajador/a a honorarios"};
                return ops[rng.nextInt(ops.length)];
            }
            case "Estudiante":
                return ("Universitaria".equals(educacion) || "Técnica".equals(educacion))
                        ? "Estudiante universitario/a" : "Estudiante";
            default:
                return situacion;
        }
    }

    private String generarNombre(Random rng, String sexo) {
        String nombre = "Femenino".equals(sexo)
                ? NOMBRES_FEMENINOS[rng.nextInt(NOMBRES_FEMENINOS.length)]
                : NOMBRES_MASCULINOS[rng.nextInt(NOMBRES_MASCULINOS.length)];
        String ap1 = APELLIDOS[rng.nextInt(APELLIDOS.length)];
        String ap2 = APELLIDOS[rng.nextInt(APELLIDOS.length)];
        return nombre + " " + ap1 + " " + ap2;
    }

    // ── Helpers de sampleo ────────────────────────────────────────────────────
    private int samplearEdad(Random rng, Integer edadMin, Integer edadMax) {
        int minEd = edadMin != null ? Math.max(edadMin, 18) : 18;
        int maxEd = edadMax != null ? Math.min(edadMax, 90) : 90;
        if (minEd > maxEd) {
            minEd = 18;
            maxEd = 90;
        }

        double[] pesosEfectivos = new double[7];
        for (int i = 0; i < 7; i++) {
            int bMin = Math.max(minEd, EDAD_MIN_BUCKET[i]);
            int bMax = Math.min(maxEd, EDAD_MAX_BUCKET[i]);
            if (bMin > bMax) {
                pesosEfectivos[i] = 0;
            } else {
                double overlapProp = (double) (bMax - bMin + 1) / (EDAD_MAX_BUCKET[i] - EDAD_MIN_BUCKET[i] + 1);
                pesosEfectivos[i] = PESOS_EDAD[i] * overlapProp;
            }
        }

        int idx = samplearIndiceConPesos(pesosEfectivos, rng);
        int bMin = Math.max(minEd, EDAD_MIN_BUCKET[idx]);
        int bMax = Math.min(maxEd, EDAD_MAX_BUCKET[idx]);
        return bMin + rng.nextInt(bMax - bMin + 1);
    }

    private String samplearConPesos(String[] opciones, double[] pesos, Random rng) {
        return opciones[samplearIndiceConPesos(pesos, rng)];
    }

    private int samplearIndiceConPesos(double[] pesos, Random rng) {
        double total = 0;
        for (double p : pesos) {
            total += p;
        }
        double r = rng.nextDouble() * total;
        double acum = 0;
        for (int i = 0; i < pesos.length; i++) {
            acum += pesos[i];
            if (r < acum) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PerfilSintetico — POJO inmutable, sin JPA
    // ═════════════════════════════════════════════════════════════════════════
    public static class PerfilSintetico {

        private final String nombre;
        private final int edad;
        private final String sexo;
        private final String pais;
        private final String region;
        private final String ciudad;
        private final String zona;
        private final String educacion;
        private final String ocupacion;
        private final String nivelSocioeconomico;
        private final String previsionSalud;
        private final String religion;
        private final String personalidad;
        private final String sensibilidadPrecio;
        private final String adopcionTecnologica;
        private final String redesSociales;
        private final String transporte;
        private final String posturaPolitica;
        private final String lealtadMarca;
        private final String alimentacion;
        private final String banco;
        private final String endeudamiento;

        public PerfilSintetico(String nombre, int edad, String sexo, String pais, String region,
                String ciudad, String zona, String educacion, String ocupacion,
                String nivelSocioeconomico, String previsionSalud, String religion,
                String personalidad, String sensibilidadPrecio, String adopcionTecnologica,
                String redesSociales, String transporte, String posturaPolitica,
                String lealtadMarca, String alimentacion, String banco, String endeudamiento) {
            this.nombre = nombre;
            this.edad = edad;
            this.sexo = sexo;
            this.pais = pais;
            this.region = region;
            this.ciudad = ciudad;
            this.zona = zona;
            this.educacion = educacion;
            this.ocupacion = ocupacion;
            this.nivelSocioeconomico = nivelSocioeconomico;
            this.previsionSalud = previsionSalud;
            this.religion = religion;
            this.personalidad = personalidad;
            this.sensibilidadPrecio = sensibilidadPrecio;
            this.adopcionTecnologica = adopcionTecnologica;
            this.redesSociales = redesSociales;
            this.transporte = transporte;
            this.posturaPolitica = posturaPolitica;
            this.lealtadMarca = lealtadMarca;
            this.alimentacion = alimentacion;
            this.banco = banco;
            this.endeudamiento = endeudamiento;
        }

        /**
         * Genera el texto que va como system prompt a Claude. Reemplaza a
         * Persona.toPromptContext() con atributos psicográficos adicionales.
         */
        public String toPromptContext() {
            return String.format(
                    "Eres %s, %d años, %s, de %s, %s, Chile. "
                    + "Educación: %s. Ocupación: %s. NSE: %s. Zona: %s. "
                    + "Previsión de salud: %s. Religión: %s. "
                    + "Personalidad: %s; %s. "
                    + "Adopción tecnológica: %s. Redes sociales: %s. "
                    + "Transporte habitual: %s. Postura política: %s. "
                    + "Lealtad a marcas: %s. Alimentación: %s. "
                    + "Servicios financieros: %s. Endeudamiento: %s.\n\n"
                    + "CÓMO RESPONDER:\n"
                    + "- Habla como chileno/a real, no como analista. Usa lenguaje cotidiano.\n"
                    + "- Algunos chilenos responsabilizan al gobierno, otros aceptan factores externos — depende de tu perfil político y NSE.\n"
                    + "- Si tu postura política es derecha o centro derecha, tiendes a aceptar más los factores económicos externos.\n"
                    + "- Si tu postura es izquierda o centro izquierda, tiendes a culpar más al gobierno y las empresas.\n"
                    + "- Si eres NSE Bajo o Medio-bajo, tu respuesta refleja escasez concreta, no abstracta.\n"
                    + "- Si eres de región o zona rural, menciona cómo lo sientes diferente al centro.\n"
                    + "- La emoción y la queja son válidas, pero no todos están igualmente enojados.\n"
                    + "- Varía el inicio. Nunca empieces con 'Mira' ni 'Honestamente'.\n"
                    + "- Máximo 3 oraciones. Primera persona.",
                    nombre, edad, sexo, ciudad, region,
                    educacion, ocupacion, nivelSocioeconomico, zona,
                    previsionSalud, religion,
                    personalidad, sensibilidadPrecio,
                    adopcionTecnologica, redesSociales,
                    transporte, posturaPolitica,
                    lealtadMarca, alimentacion,
                    banco, endeudamiento
            );
        }

        /**
         * Resumen breve para guardar en Respuesta.perfilResumen. Ej: "María
         * González Muñoz, 34 años, Femenino, Santiago, Universitaria,
         * Medio-alto"
         */
        public String toResumen() {
            return nombre + ", " + edad + " años, " + sexo + ", " + ciudad + ", " + educacion + ", " + nivelSocioeconomico;
        }

        public String getNombre() {
            return nombre;
        }

        public int getEdad() {
            return edad;
        }

        public String getSexo() {
            return sexo;
        }

        public String getPais() {
            return pais;
        }

        public String getRegion() {
            return region;
        }

        public String getCiudad() {
            return ciudad;
        }

        public String getZona() {
            return zona;
        }

        public String getEducacion() {
            return educacion;
        }

        public String getOcupacion() {
            return ocupacion;
        }

        public String getNivelSocioeconomico() {
            return nivelSocioeconomico;
        }

        public String getPrevisionSalud() {
            return previsionSalud;
        }

        public String getReligion() {
            return religion;
        }

        public String getPersonalidad() {
            return personalidad;
        }

        public String getSensibilidadPrecio() {
            return sensibilidadPrecio;
        }

        public String getAdopcionTecnologica() {
            return adopcionTecnologica;
        }

        public String getRedesSociales() {
            return redesSociales;
        }

        public String getTransporte() {
            return transporte;
        }

        public String getPosturaPolitica() {
            return posturaPolitica;
        }

        public String getLealtadMarca() {
            return lealtadMarca;
        }

        public String getAlimentacion() {
            return alimentacion;
        }

        public String getBanco() {
            return banco;
        }

        public String getEndeudamiento() {
            return endeudamiento;
        }
    }
}
