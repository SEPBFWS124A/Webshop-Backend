package de.fhdw.webshop.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * #128 — Sets Cache-Control headers on all API responses.
 *
 * Non-PII read endpoints (product catalogue, advertisements) get a short public cache
 * so CDN edge nodes and browsers can cache them without re-hitting the origin.
 *
 * PII endpoints (cart, orders, user data, admin) always get no-store so that
 * personal data is never cached at proxy or CDN level — GDPR compliance.
 *
 * All mutating requests (POST/PUT/PATCH/DELETE) are always no-store.
 */
@Component
public class CacheControlFilter implements Filter {

    private static final String PUBLIC_CACHE = "public, max-age=300, must-revalidate";
    private static final String NO_STORE = "no-store, no-cache, must-revalidate, private";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD");

    // Prefixes whose responses may be publicly cached (non-PII, read-only)
    private static final Set<String> PUBLIC_CACHEABLE_PREFIXES = Set.of(
            "/api/products",
            "/api/product-bundles/active",
            "/api/advertisements/active",
            "/api/marketplace/products",
            "/api/marketplace/sellers",
            "/api/seller-reviews/seller",
            "/api/pickup-stores",
            "/api/agb/latest"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (SAFE_METHODS.contains(method) && isPubliclyCacheable(path)) {
            response.setHeader("Cache-Control", PUBLIC_CACHE);
            response.setHeader("Vary", "Accept-Encoding, Accept-Language");
        } else {
            response.setHeader("Cache-Control", NO_STORE);
            response.setHeader("Pragma", "no-cache");
        }

        chain.doFilter(request, response);
    }

    private boolean isPubliclyCacheable(String path) {
        return PUBLIC_CACHEABLE_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
