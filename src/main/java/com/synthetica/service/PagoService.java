package com.synthetica.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
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
import java.util.List;
import java.util.Map;

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
        "STARTER", new PlanInfo("Plan Starter - Synthetica", new BigDecimal("12.00"), 500, Usuario.Plan.STARTER),
        "PRO",     new PlanInfo("Plan Pro - Synthetica",     new BigDecimal("39.00"), 2000, Usuario.Plan.PRO)
    );

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;

    @Value("${mp.access-token}")
    private String mpAccessToken;

    @Value("${mp.sandbox:true}")
    private boolean sandbox;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    public PagoService(UsuarioRepository usuarioRepository,
                       SuscripcionRepository suscripcionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    public String crearPreferencia(Long usuarioId, String planNombre) {
        PlanInfo config = PLANES.get(planNombre.toUpperCase());
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Plan no válido. Opciones: STARTER, PRO");
        }

        try {
            MercadoPagoConfig.setAccessToken(mpAccessToken);

            PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(planNombre.toUpperCase())
                .title(config.getTitulo())
                .quantity(1)
                .unitPrice(config.getPrecio())
                .currencyId("USD")
                .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl + "/pagos/exito")
                .failure(frontendUrl + "/pagos/error")
                .pending(frontendUrl + "/pagos/pendiente")
                .build();

            PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                .notificationUrl(backendUrl + "/api/pagos/webhook")
                .externalReference(usuarioId + "|" + planNombre.toUpperCase())
                .build();

            Preference preference = new PreferenceClient().create(request);

            return sandbox ? preference.getSandboxInitPoint() : preference.getInitPoint();

        } catch (MPApiException e) {
            log.error("MercadoPago API error - status: {}, body: {}",
                e.getStatusCode(), e.getApiResponse() != null ? e.getApiResponse().getContent() : "null");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al iniciar el pago: " + e.getMessage());
        } catch (MPException e) {
            log.error("MercadoPago error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error al iniciar el pago. Intenta nuevamente.");
        }
    }

    public void procesarWebhook(Map<String, Object> body, String queryId, String topic) {
        try {
            Long paymentId = resolverPaymentId(body, queryId, topic);
            if (paymentId == null) return;

            MercadoPagoConfig.setAccessToken(mpAccessToken);
            Payment payment = new PaymentClient().get(paymentId);

            if (!"approved".equals(payment.getStatus())) {
                log.info("Pago {} con estado {}, ignorado", paymentId, payment.getStatus());
                return;
            }

            // Evitar procesar el mismo pago dos veces
            if (suscripcionRepository.existsByMpPaymentId(paymentId.toString())) {
                log.info("Pago {} ya procesado, ignorado", paymentId);
                return;
            }

            String externalRef = payment.getExternalReference();
            if (externalRef == null || !externalRef.contains("|")) {
                log.warn("externalReference inválido: {}", externalRef);
                return;
            }

            String[] partes = externalRef.split("\\|", 2);
            Long usuarioId = Long.parseLong(partes[0]);
            String planNombre = partes[1];

            PlanInfo config = PLANES.get(planNombre);
            if (config == null) {
                log.warn("Plan desconocido en pago {}: {}", paymentId, planNombre);
                return;
            }

            Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
            if (usuario == null) {
                log.warn("Usuario {} no encontrado para pago {}", usuarioId, paymentId);
                return;
            }

            usuario.setPlan(config.getPlan());
            usuario.setCreditosTotal(usuario.getCreditosTotal() + config.getCreditos());
            usuarioRepository.save(usuario);

            Suscripcion suscripcion = new Suscripcion();
            suscripcion.setUsuario(usuario);
            suscripcion.setMpPaymentId(paymentId.toString());
            suscripcion.setPlan(planNombre);
            suscripcion.setMonto(config.getPrecio());
            suscripcion.setEstado("approved");
            suscripcion.setCreditosOtorgados(config.getCreditos());
            suscripcionRepository.save(suscripcion);

            log.info("Pago {} procesado: usuario {} -> plan {} +{} créditos",
                paymentId, usuarioId, planNombre, config.getCreditos());

        } catch (MPException | MPApiException e) {
            log.error("Error al consultar pago en MercadoPago: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al procesar webhook: {}", e.getMessage());
        }
    }

    private Long resolverPaymentId(Map<String, Object> body, String queryId, String topic) {
        // Formato IPN: query params ?id=xxx&topic=payment
        if ("payment".equals(topic) && queryId != null) {
            return Long.parseLong(queryId);
        }

        // Formato Webhook: body JSON {"type":"payment","data":{"id":"xxx"}}
        if (body != null) {
            String type = (String) body.get("type");
            String action = (String) body.get("action");

            boolean esEvento = "payment".equals(type)
                || "payment.created".equals(action)
                || "payment.updated".equals(action);

            if (esEvento) {
                Object data = body.get("data");
                if (data instanceof Map<?, ?> dataMap) {
                    Object idObj = dataMap.get("id");
                    if (idObj != null) {
                        return Long.parseLong(idObj.toString());
                    }
                }
            }
        }

        return null;
    }
}
