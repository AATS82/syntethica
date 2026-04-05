package com.synthetica.service;

import com.synthetica.model.Usuario;
import com.synthetica.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Usuario descontarCreditos(String emailUsuario, int cantidad) {
        Usuario usuario = usuarioRepository.findByEmailForUpdate(emailUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        int disponibles = usuario.getCreditosTotal() - usuario.getCreditosUsados();
        if (disponibles < cantidad) {
            throw new RuntimeException("Créditos insuficientes. Necesitas " + cantidad
                    + " créditos pero tienes " + disponibles + " disponibles.");
        }
        usuario.setCreditosUsados(usuario.getCreditosUsados() + cantidad);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void devolverCreditos(String emailUsuario, int cantidad) {
        Usuario usuario = usuarioRepository.findByEmailForUpdate(emailUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado al devolver créditos"));
        usuario.setCreditosUsados(Math.max(0, usuario.getCreditosUsados() - cantidad));
        usuarioRepository.save(usuario);
        log.info("[CREDITOS] {} créditos devueltos a {}", cantidad, emailUsuario);
    }
}
