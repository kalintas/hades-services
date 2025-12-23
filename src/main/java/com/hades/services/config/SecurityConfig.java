package com.hades.services.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.hades.services.model.Role;
import com.hades.services.security.annotation.Access;
import com.hades.services.security.mapper.FirebaseAuthenticationTokenConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.HashMap;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthenticationTokenConverter firebaseAuthenticationTokenConverter;

    @Bean
    public RoleHierarchy roleHierarchy() {
        return Role.getRoleHierarchy();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);
        return expressionHandler;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", firebaseToken.getUid())
                        .claim("email", firebaseToken.getEmail())
                        .claims(claims -> claims.putAll(firebaseToken.getClaims()))
                        .issuedAt(Instant.ofEpochSecond(System.currentTimeMillis() / 1000)) // Approximate
                        .expiresAt(Instant.ofEpochSecond(System.currentTimeMillis() / 1000 + 3600)) // Approximate,
                                                                                                    // valid since
                                                                                                    // verified
                        .build();
            } catch (Exception e) {
                throw new JwtException("Invalid Firebase Token", e);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RequestMappingHandlerMapping handlerMapping)
            throws Exception {
        String[] publicPaths = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().hasMethodAnnotation(Access.Public.class))
                .flatMap(entry -> {
                    var patterns = entry.getKey().getPatternValues();
                    if (patterns == null) {
                        return Stream.empty();
                    }
                    return patterns.stream();
                })
                .toArray(String[]::new);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/chat/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/earthquakes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/drones/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/users/*/profile").permitAll()
                        .requestMatchers(publicPaths).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new com.hades.services.security.web.CookieBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(firebaseAuthenticationTokenConverter)
                                .decoder(jwtDecoder())));

        return http.build();
    }
}
