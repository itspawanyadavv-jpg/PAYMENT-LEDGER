package com.aman.payment.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Filter that validates Bearer JWT tokens or mock tokens.
 * Extracts user identities and assigned roles (such as ROLE_FINANCE_ADMIN or ROLE_USER)
 * and populates the Spring SecurityContextHolder.
 */
@Component
@Slf4j
public class MockJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UsernamePasswordAuthenticationToken authentication = parseToken(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated principal '{}' with authorities {}",
                            authentication.getPrincipal(), authentication.getAuthorities());
                } catch (Exception e) {
                    log.warn("Failed to parse Bearer token: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken parseToken(String token) {
        String principal = "user";
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (token.contains(".")) {
            // Decode standard JWT payload
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode claims = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
                    if (claims.has("sub")) {
                        principal = claims.get("sub").asText();
                    }
                    if (claims.has("roles")) {
                        JsonNode rolesNode = claims.get("roles");
                        if (rolesNode.isArray()) {
                            for (JsonNode r : rolesNode) {
                                String role = r.asText();
                                if (!role.startsWith("ROLE_")) {
                                    role = "ROLE_" + role;
                                }
                                authorities.add(new SimpleGrantedAuthority(role));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Mock token fallback rules
        if (authorities.isEmpty()) {
            String lower = token.toLowerCase();
            if (lower.contains("finance") || lower.contains("admin")) {
                principal = "finance_admin";
                authorities.add(new SimpleGrantedAuthority("ROLE_FINANCE_ADMIN"));
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            } else {
                principal = "standard_user";
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
