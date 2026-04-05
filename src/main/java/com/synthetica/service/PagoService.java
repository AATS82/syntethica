package com.synthetica.service;

import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.common.IntegrationType;
import cl.transbank.webpay.common.WebpayOptions;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import com.synthetica.model.Suscripcion;
import com.synthetica.model.Usuario;
import com.synthetica.repository.SuscripcionRepository;
import com.synthetica.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    public static class PlanInfo {
        private final String titulo;
        private final BigDecimal precio;
        private final Usuario.Plan plan;

        public PlanInfo(String titulo, BigDecimal precio, Usuario.Plan plan) {
            this.titulo = titulo;
            this.precio = precio;
            this.plan = plan;
        }

        public String getTitulo() { return titulo; }
        public BigDecimal getPrecio() { return precio; }
        public int getCreditos() { return plan.getCreditosBase(); }
        public Usuario.Plan getPlan() { return plan; }
    }

    public static final Map<String, PlanInfo> PLANES = Map.of(
        "STARTER",  new PlanInfo("Plan Starter - Synthetica",  new BigDecimal("4990"),  Usuario.Plan.STARTER),
        "BASIC",    new PlanInfo("Plan Basic - Synthetica",    new BigDecimal("8990"),  Usuario.Plan.BASIC),
        "PRO",      new PlanInfo("Plan Pro - Synthetica",      new BigDecimal("22990"), Usuario.Plan.PRO),
        "BUSINESS", new PlanInfo("Plan Business - Synthetica", new BigDecimal("64990"), Usuario.Plan.BUSINESS)
    );

    public static class CreditPackInfo {
        private final String titulo;
        private final BigDecimal precio;
        private final int creditos;

        public CreditPackInfo(String titulo, BigDecimal precio, int creditos) {
            this.titulo = titulo;
            this.precio = precio;
            this.creditos = creditos;
        }

        public String getTitulo() { return titulo; }
        public BigDecimal getPrecio() { return precio; }
        public int getCreditos() { return creditos; }
    }

    public static final Map<String, CreditPackInfo> CREDIT_PACKS = Map.of(
        "PACK_MINI",  new CreditPackInfo("Recarga Mini - Synthetica",   new BigDecimal("2490"),  200),
        "PACK_MEDIA", new CreditPackInfo("Recarga Media - Synthetica",  new BigDecimal("4990"),  500),
        "PACK_GRANDE",new CreditPackInfo("Recarga Grande - Synthetica", new BigDecimal("8490"), 1000)
    );

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;

    @Value("${transbank.commerce-code:#{null}}")
    private String commerceCode;

    @Value("${transbank.api-key:#{null}}")
    private String apiKey;

    @Value("${transbank.environment:integration}")
    private String environment;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public PagoService(UsuarioRepository usuarioRepository,
                       SuscripcionRepository suscripcionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    private WebpayPlus.Transaction buildTransaction() {
        if ("production".equalsIgnoreCase(environment) && commerceCode != null && apiKey != null) {
            return new WebpayPlus.Transaction(
                new WebpayOptions(commerceCode, apiKey, IntegrationType.LIVE)
            );
        }
        // Credenciales de integración (testing)
        return new WebpayPlus.Transaction(
            new WebpayOptions(IntegrationCommerceCodes.WEBPAY_PLUS, IntegrationApiKeys.WEBPAY, IntegrationType.TEST)
        );
    }

    public Map<String, String> iniciarTransaccion(Long usuarioId, String planNombre) {
        String key = planNombre.toUpperCase();
        double amount;

        if (PLANES.containsKey(key)) {
            amount = PLANES.get(key).getPrecio().doubleValue();
        } else if (CREDIT_PACKS.containsKey(key)) {
            amount = CREDIT_PACKS.get(key).getPrecio().doubleValue();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Producto no válido. Planes: STARTER, BASIC, PRO, BUSINESS. Paquetes: PACK_MINI, PACK_MEDIA, PACK_GRANDE");
        }

        String buyOrder = usuarioId + "-" + key + "-" + System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();
        String returnUrl = backendUrl + "/api/pagos/confirmar";

        try {
            WebpayPlusTransactionCreateResponse response = buildTransaction()
                .create(buyOrder, sessionId, amount, returnUrl);

            log.info("Transacción Transbank creada: buyOrder={}, token={}", buyOrder, response.getToken());

            return Map.of(
                "token", response.getToken(),
                "url",   response.getUrl()
            );
        } catch (Exception e) {
            log.error("Error al crear transacción Transbank: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al iniciar el pago. Intenta nuevamente.");
        }
    }

    public String confirmarTransaccion(String token) {
        if (token == null) {
            log.info("token_ws nulo: pago cancelado o con timeout");
            return frontendUrl + "/pagos/error?motivo=cancelado";
        }

        if (suscripcionRepository.existsByTbkToken(token)) {
            log.info("Token {} ya procesado, redirigiendo a éxito", token);
            return frontendUrl + "/pagos/exito";
        }

        try {
            WebpayPlusTransactionCommitResponse response = buildTransaction().commit(token);

            if (!"AUTHORIZED".equals(response.getStatus())) {
                log.warn("Transacción {} con estado {}", token, response.getStatus());
                return frontendUrl + "/pagos/error?status=" + response.getStatus();
            }

            // buyOrder formato: "userId-PLAN-timestamp"
            String buyOrder = response.getBuyOrder();
            String[] partes = buyOrder != null ? buyOrder.split("-", 3) : new String[0];
            if (partes.length < 2) {
                log.error("buyOrder con formato inválido: '{}' para token {}. Pago procesado pero no acreditado — revisión manual requerida.", buyOrder, token);
                return frontendUrl + "/pagos/error?motivo=formato_invalido";
            }
            Long usuarioId;
            try {
                usuarioId = Long.parseLong(partes[0]);
            } catch (NumberFormatException e) {
                log.error("userId no numérico en buyOrder '{}' para token {}. Revisión manual requerida.", buyOrder, token);
                return frontendUrl + "/pagos/error?motivo=formato_invalido";
            }
            String planNombre = partes[1];

            Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
            if (usuario == null) {
                log.warn("Usuario {} no encontrado para token {}", usuarioId, token);
                return frontendUrl + "/pagos/error";
            }

            int creditosOtorgados;
            BigDecimal monto;

            if (PLANES.containsKey(planNombre)) {
                PlanInfo config = PLANES.get(planNombre);
                usuario.setPlan(config.getPlan());
                usuario.setCreditosTotal(usuario.getCreditosTotal() + config.getCreditos());
                creditosOtorgados = config.getCreditos();
                monto = config.getPrecio();
                log.info("Pago Transbank confirmado: usuario {} -> plan {} +{} créditos",
                    usuarioId, planNombre, creditosOtorgados);
            } else if (CREDIT_PACKS.containsKey(planNombre)) {
                CreditPackInfo pack = CREDIT_PACKS.get(planNombre);
                usuario.setCreditosTotal(usuario.getCreditosTotal() + pack.getCreditos());
                creditosOtorgados = pack.getCreditos();
                monto = pack.getPrecio();
                log.info("Pago Transbank confirmado: usuario {} -> paquete {} +{} créditos",
                    usuarioId, planNombre, creditosOtorgados);
            } else {
                log.warn("Producto desconocido en buyOrder {}", buyOrder);
                return frontendUrl + "/pagos/error";
            }

            usuarioRepository.save(usuario);

            Suscripcion suscripcion = new Suscripcion();
            suscripcion.setUsuario(usuario);
            suscripcion.setTbkToken(token);
            suscripcion.setPlan(planNombre);
            suscripcion.setMonto(monto);
            suscripcion.setEstado("AUTHORIZED");
            suscripcion.setCreditosOtorgados(creditosOtorgados);
            suscripcionRepository.save(suscripcion);

            return frontendUrl + "/pagos/exito";

        } catch (Exception e) {
            log.error("Error al confirmar transacción Transbank {}: {}", token, e.getMessage());
            return frontendUrl + "/pagos/error";
        }
    }
}
