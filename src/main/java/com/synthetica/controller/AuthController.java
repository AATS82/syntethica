package com.synthetica.controller;

import com.synthetica.model.Usuario;
import com.synthetica.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    public AuthController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String email = (String) auth.getPrincipal();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }

        return ResponseEntity.ok(Map.of(
            "id",              usuario.getId(),
            "email",           usuario.getEmail(),
            "nombre",          usuario.getNombre() != null ? usuario.getNombre() : "",
            "foto",            usuario.getFoto() != null ? usuario.getFoto() : "",
            "plan",            usuario.getPlan().name(),
            "creditosUsados",  usuario.getCreditosUsados(),
            "creditosTotal",   usuario.getCreditosTotal(),
            "creadoEn",        usuario.getCreadoEn().toString()
        ));
    }
}
