package com.vanter.ember.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper;

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/sessions/*/status").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/printing/agents/token").permitAll()
                        .requestMatchers("/printing/agents/me/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // Hub's bundled frontend shell (HubWebConfig, @Profile("hub")) — "/app"
                        // is deliberately not used by any real controller, so this can never
                        // shadow a protected API route (e.g. /kitchen/orders is both a real KDS
                        // endpoint AND a frontend route, which is exactly why the frontend is
                        // served from its own prefix instead of path-matched at the root).
                        .requestMatchers("/app/**").permitAll()
                        // The Hub's one-time activation call — authenticates via the license
                        // signature itself (HubActivationService), not a bearer token.
                        .requestMatchers("/hub-activations").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    chain.doFilter(request, response);
                    return;
                }
                String token = authHeader.substring(7);
                if (!jwtService.isTokenValid(token)) {
                    chain.doFilter(request, response);
                    return;
                }
                // A print-agent token's subject is the agent's own id, not a user email — it
                // has no corresponding User row, so loadUserByUsername would always throw
                // UsernameNotFoundException. This token type authenticates the caller (a print
                // agent hitting one of its own permitAll /printing/agents/** routes) by its
                // signature alone; it still needs TenantContextHolder bound below, just not a
                // Spring Security principal.
                String type = jwtService.extractClaim(token, claims -> claims.get("typ", String.class));
                if (!"print-agent".equals(type)) {
                    String email = jwtService.extractSubject(token);
                    if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        // A deactivated staff account's JWT can still be within its validity window —
                        // simply not authenticating here lets the existing anyRequest().authenticated()
                        // rule reject it the same way an absent/invalid token already does.
                        if (userDetails.isEnabled()) {
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
                UUID tenantId = jwtService.extractTenantId(token);
                TenantContextHolder.setTenantId(tenantId);
                try {
                    // A CUSTOMER who hasn't joined a table yet has no restaurant on their token
                    // (see AuthService#tenantIdOf) — they can't be gated on a tenant they haven't
                    // chosen. Letting them through is safe because every tenant-scoped read still
                    // fails closed on the absent TenantContextHolder; the session-join flow is the
                    // one path that resolves a restaurant, and it re-checks status itself.
                    if (tenantId == null && isCustomer()) {
                        chain.doFilter(request, response);
                        return;
                    }
                    Restaurant restaurant = tenantId != null
                            ? restaurantRepository.findById(tenantId).orElse(null)
                            : null;
                    if (restaurant == null || restaurant.getStatus() != RestaurantStatus.ACTIVE) {
                        writeSuspendedTenantResponse(request, response, restaurant);
                        return;
                    }
                    chain.doFilter(request, response);
                } finally {
                    TenantContextHolder.clear();
                }
            }

            private boolean isCustomer() {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                return authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(granted -> "ROLE_CUSTOMER".equals(granted.getAuthority()));
            }

            private void writeSuspendedTenantResponse(HttpServletRequest request,
                                                       HttpServletResponse response,
                                                       Restaurant restaurant) throws IOException {
                String detail = restaurant == null
                        ? "Tenant account not found."
                        : "This tenant account is " + restaurant.getStatus().name().toLowerCase()
                                + "; access is blocked pending resolution.";
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
                problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
                problem.setInstance(URI.create(request.getRequestURI()));
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/problem+json");
                objectMapper.writeValue(response.getWriter(), problem);
            }
        };
    }
}
