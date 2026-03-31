package com.synthetica.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Lista negra en memoria para tokens JWT invalidados (logout).
 * Los tokens se limpian automáticamente cuando expiran.
 */
@Component
public class TokenBlacklist {

    // token -> expiry timestamp en milisegundos
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public void add(String token, long expiryMs) {
        purgeExpired();
        blacklist.put(token, expiryMs);
    }

    public boolean contains(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> now > e.getValue());
    }
}
