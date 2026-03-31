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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        private final int creditos;
        private final Usuario.Plan plan;

        public PlanInfo(String titulo, BigDecimal precio, int creditos, Usuario.Plan plan) {
            this.titulo = titulo;
            this.precio = precio;
            this.creditos = creditos;
            this.plan = plan;
        }

        public String getTitulo() { return titulo; }
        public BigDecimal getPrecio() { return precio; }
        public int getCreditos() { return creditos; }
        public Usuario.Plan getPlan() { return plan; }
    }

    public static final Map<String, PlanInfo> PLANES = Map.of(
        "STARTER", new PlanInfo("Plan Starter - Synthetica", new BigDecimal("11000"), 500, Usuario.Plan.STARTER),
        "PRO",     new PlanInfo("Plan Pro - Synthetica",     new BigDecimal("36000"), 2000, Usuario.Plan.PRO)
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
        PlanInfo config = PLANES.get(planNombre.toUpperCase());
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Plan no válido. Opciones: STARTER, PRO");
        }

        String buyOrder = usuarioId + "-" + planNombre.toUpperCase() + "-" + System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();
        double amount = config.getPrecio().doubleValue();
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

    @Transactional
    public String confirmarTransaccion(String token) {
        if (token == null) {
            log.info("token_ws nulo: pago cancelado o con timeout");
            return frontendUrl + "/pagos/error?motivo=cancelado";
        }

        // Idempotencia: si ya fue procesado, redirigir a éxito sin re-procesar
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
            String[] partes = buyOrder.split("-", 3);
            if (partes.length < 2) {
                log.error("buyOrder con formato inválido: {}", buyOrder);
                return frontendUrl + "/pagos/error";
            }

            Long usuarioId;
            try {
                usuarioId = Long.parseLong(partes[0]);
            } catch (NumberFormatException e) {
                log.error("usuarioId inválido en buyOrder {}: {}", buyOrder, partes[0]);
                return frontendUrl + "/pagos/error";
            }

            String planNombre = partes[1];
            PlanInfo config = PLANES.get(planNombre);
            if (config == null) {
                log.warn("Plan desconocido en buyOrder {}", buyOrder);
                return frontendUrl + "/pagos/error";
            }

            // Lock pesimista para evitar doble crédito si Transbank reintenta
            Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId).orElse(null);
            if (usuario == null) {
                log.warn("Usuario {} no encontrado para token {}", usuarioId, token);
                return frontendUrl + "/pagos/error";
            }

            usuario.setPlan(config.getPlan());
            usuario.setCreditosTotal(usuario.getCreditosTotal() + config.getCreditos());
            usuarioRepository.save(usuario);

            Suscripcion suscripcion = new Suscripcion();
            suscripcion.setUsuario(usuario);
            suscripcion.setTbkToken(token);
            suscripcion.setPlan(planNombre);
            suscripcion.setMonto(config.getPrecio());
            suscripcion.setEstado("AUTHORIZED");
            suscripcion.setCreditosOtorgados(config.getCreditos());
            suscripcionRepository.save(suscripcion);

            log.info("Pago Transbank confirmado: usuario {} -> plan {} +{} créditos",
                usuarioId, planNombre, config.getCreditos());

            return frontendUrl + "/pagos/exito";

        } catch (DataIntegrityViolationException e) {
            // Unique constraint en tbk_token: otro hilo ya procesó este token
            log.warn("Token {} ya procesado por otro hilo (constraint violation)", token);
            return frontendUrl + "/pagos/exito";
        } catch (Exception e) {
            log.error("Error al confirmar transacción Transbank {}: {}", token, e.getMessage());
            return frontendUrl + "/pagos/error";
        }
    }
}
