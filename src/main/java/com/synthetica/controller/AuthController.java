package com.synthetica.controller;

import com.synthetica.model.Usuario;
import com.synthetica.repository.UsuarioRepository;
import com.synthetica.security.JwtService;
import com.synthetica.security.TokenBlacklist;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final TokenBlacklist tokenBlacklist;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository,
                          TokenBlacklist tokenBlacklist,
                          JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenBlacklist = tokenBlacklist;
        this.jwtService = jwtService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String email = (String) auth.getPrincipal();
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }

        return ResponseEntity.ok(buildUsuarioResponse(usuario));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String email = (String) auth.getPrincipal();
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }

        if (body.containsKey("nombre")) {
            String nombre = body.get("nombre");
            if (nombre == null || nombre.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío"));
            }
            usuario.setNombre(nombre.trim());
        }

        if (body.containsKey("foto")) {
            String foto = body.get("foto");
            usuario.setFoto(foto != null ? foto.trim() : null);
        }

        usuarioRepository.save(usuario);
        return ResponseEntity.ok(buildUsuarioResponse(usuario));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                long expiryMs = jwtService.extractExpirationMs(token);
                tokenBlacklist.add(token, expiryMs);
            }
        }
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente"));
    }

    private Map<String, Object> buildUsuarioResponse(Usuario usuario) {
        return Map.of(
            "id",             usuario.getId(),
            "email",          usuario.getEmail(),
            "nombre",         usuario.getNombre() != null ? usuario.getNombre() : "",
            "foto",           usuario.getFoto() != null ? usuario.getFoto() : "",
            "plan",           usuario.getPlan().name(),
            "creditosUsados", usuario.getCreditosUsados(),
            "creditosTotal",  usuario.getCreditosTotal(),
            "creadoEn",       usuario.getCreadoEn().toString()
        );
    }
}
