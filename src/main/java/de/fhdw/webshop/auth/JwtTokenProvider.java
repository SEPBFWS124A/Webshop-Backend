package de.fhdw.webshop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and validates JWT tokens.
 * The token contains the username as subject and the user's role as a claim.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, buildAuthorityClaims(userDetails));
    }

    public String generateImpersonationToken(UserDetails targetUser, UserDetails adminUser) {
        Map<String, Object> additionalClaims = buildAuthorityClaims(targetUser);
        additionalClaims.put("impersonation", true);
        additionalClaims.put("impersonatedBy", adminUser.getUsername());
        return buildToken(targetUser, additionalClaims);
    }

    private Map<String, Object> buildAuthorityClaims(UserDetails userDetails) {
        Map<String, Object> additionalClaims = new HashMap<>();
        additionalClaims.put("authorities", userDetails.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList());
        return additionalClaims;
    }

    private String buildToken(UserDetails userDetails, Map<String, Object> additionalClaims) {
        return Jwts.builder()
                .claims(additionalClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
