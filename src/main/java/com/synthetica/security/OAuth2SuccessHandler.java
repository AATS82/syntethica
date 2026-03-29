package com.synthetica.security;

import com.synthetica.model.Usuario;
import com.synthetica.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public OAuth2SuccessHandler(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email    = oAuth2User.getAttribute("email");
        String nombre   = oAuth2User.getAttribute("name");
        String foto     = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        Usuario usuario = usuarioRepository.findByEmail(email)
                .map(u -> actualizarUsuario(u, nombre, foto, googleId))
                .orElseGet(() -> crearUsuario(email, nombre, foto, googleId));

        String token = jwtService.generateToken(usuario);

        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/oauth2/redirect?token=" + token);
    }

    private Usuario actualizarUsuario(Usuario u, String nombre, String foto, String googleId) {
        u.setNombre(nombre);
        u.setFoto(foto);
        if (u.getGoogleId() == null) {
            u.setGoogleId(googleId);
        }
        u.setUltimoAcceso(LocalDateTime.now());
        return usuarioRepository.save(u);
    }

    private Usuario crearUsuario(String email, String nombre, String foto, String googleId) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setNombre(nombre);
        u.setFoto(foto);
        u.setGoogleId(googleId);
        u.setUltimoAcceso(LocalDateTime.now());
        return usuarioRepository.save(u);
    }
}
