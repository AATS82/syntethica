package com.synthetica.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public SecurityConfig(OAuth2SuccessHandler oAuth2SuccessHandler,
                          JwtAuthFilter jwtAuthFilter) {
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos
                .requestMatchers(
                    "/api/encuesta-rapida/*/progreso",
                    "/api/encuesta-rapida/*/resultados",
                    "/api/personas",
                    "/api/personas/*",
                    "/api/pagos/confirmar",
                    "/api/pagos/planes",
                    "/api/auth/logout",
                    "/oauth2/**",
                    "/login/**"
                ).permitAll()
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // API requests get 401 instead of OAuth redirect
                    String accept = request.getHeader("Accept");
                    String requestedWith = request.getHeader("X-Requested-With");
                    boolean isApiRequest = (accept != null && accept.contains("application/json"))
                            || "XMLHttpRequest".equals(requestedWith)
                            || request.getRequestURI().startsWith("/api/");
                    if (isApiRequest) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    } else {
                        response.sendRedirect("/oauth2/authorization/google");
                    }
                })
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:4200",
            "http://localhost:3000",
            frontendUrl
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
