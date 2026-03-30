package com.synthetica.controller;

import com.synthetica.model.Suscripcion;
import com.synthetica.repository.SuscripcionRepository;
import com.synthetica.service.PagoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final SuscripcionRepository suscripcionRepository;

    @Value("${mp.public-key}")
    private String mpPublicKey;

    public PagoController(PagoService pagoService,
                          SuscripcionRepository suscripcionRepository) {
        this.pagoService = pagoService;
        this.suscripcionRepository = suscripcionRepository;
    }

    // Configuración pública de MP para el frontend
    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(Map.of("publicKey", mpPublicKey));
    }

    // Planes disponibles (público, para mostrar en la landing)
    @GetMapping("/planes")
    public ResponseEntity<?> planes() {
        List<Map<String, Object>> planes = PagoService.PLANES.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "id",       e.getKey(),
                "titulo",   e.getValue().getTitulo(),
                "precio",   e.getValue().getPrecio(),
                "creditos", e.getValue().getCreditos()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(planes);
    }

    // Crea la preferencia y devuelve la URL de pago
    @PostMapping("/crear-preferencia")
    public ResponseEntity<?> crearPreferencia(@RequestBody Map<String, String> body,
                                               Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String plan = body.get("plan");
        if (plan == null || plan.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'plan' es requerido"));
        }

        Long usuarioId = (Long) auth.getDetails();
        String checkoutUrl = pagoService.crearPreferencia(usuarioId, plan);

        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }

    // Webhook de MercadoPago (debe ser público)
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) String id,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(required = false) String topic) {
        String resolvedId = (id != null) ? id : dataId;
        pagoService.procesarWebhook(body, resolvedId, topic);
        return ResponseEntity.ok().build();
    }

    // Historial de pagos del usuario autenticado
    @GetMapping("/historial")
    public ResponseEntity<?> historial(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        Long usuarioId = (Long) auth.getDetails();
        List<Suscripcion> historial = suscripcionRepository
            .findByUsuarioIdOrderByCreadoEnDesc(usuarioId);

        List<Map<String, Object>> response = historial.stream()
            .map(s -> Map.<String, Object>of(
                "id",                s.getId(),
                "plan",              s.getPlan(),
                "monto",             s.getMonto(),
                "estado",            s.getEstado(),
                "creditosOtorgados", s.getCreditosOtorgados(),
                "creadoEn",          s.getCreadoEn().toString()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
