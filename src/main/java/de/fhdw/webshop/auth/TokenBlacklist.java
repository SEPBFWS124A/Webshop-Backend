package de.fhdw.webshop.auth;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for invalidated JWT tokens (logout).
 * Tokens are kept until they expire; this store is intentionally process-local
 * (acceptable for a single-instance deployment; replace with Redis for multi-node).
 */
@Component
public class TokenBlacklist {

    private final Set<String> invalidatedTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void invalidate(String token) {
        invalidatedTokens.add(token);
    }

    public boolean isInvalidated(String token) {
        return invalidatedTokens.contains(token);
    }
}
