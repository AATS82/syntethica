package com.synthetica.controller;

import com.synthetica.model.Suscripcion;
import com.synthetica.repository.SuscripcionRepository;
import com.synthetica.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final SuscripcionRepository suscripcionRepository;

    public PagoController(PagoService pagoService,
                          SuscripcionRepository suscripcionRepository) {
        this.pagoService = pagoService;
        this.suscripcionRepository = suscripcionRepository;
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

    // Paquetes de créditos disponibles (público)
    @GetMapping("/paquetes")
    public ResponseEntity<?> paquetes() {
        List<Map<String, Object>> paquetes = PagoService.CREDIT_PACKS.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "id",       e.getKey(),
                "titulo",   e.getValue().getTitulo(),
                "precio",   e.getValue().getPrecio(),
                "creditos", e.getValue().getCreditos()
            ))
            .sorted((a, b) -> Integer.compare((int) a.get("creditos"), (int) b.get("creditos")))
            .collect(Collectors.toList());
        return ResponseEntity.ok(paquetes);
    }

    // Inicia una transacción Transbank y devuelve token + URL de pago
    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarPago(@RequestBody Map<String, String> body,
                                          Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String plan = body.get("plan");
        if (plan == null || plan.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El campo 'plan' es requerido"));
        }

        Long usuarioId = (Long) auth.getDetails();
        Map<String, String> result = pagoService.iniciarTransaccion(usuarioId, plan);

        return ResponseEntity.ok(result);
    }

    // Transbank redirige aquí (POST o GET) con token_ws tras el pago, o TBK_TOKEN si fue cancelado
    @RequestMapping(value = "/confirmar", method = { RequestMethod.POST, RequestMethod.GET })
    public ResponseEntity<Void> confirmarPago(
            @RequestParam(name = "token_ws", required = false) String token,
            @RequestParam(name = "TBK_TOKEN", required = false) String tbkCancelToken) {
        // Si solo está TBK_TOKEN (sin token_ws), el usuario canceló el pago
        String redirectUrl = pagoService.confirmarTransaccion(token);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectUrl))
            .build();
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
