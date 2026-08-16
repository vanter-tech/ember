package com.vanter.ember.platform.config;

import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Isolated {@code /platform/**} auth chain for the super-admin console (EMB-PC-04). Signed with
 * {@link PlatformJwtService}'s own {@code platform.jwt.secret}, loads operators via
 * {@link PlatformOperatorDetailsService} injected by concrete type (the tenant
 * {@code UserDetailsService} bean is {@code @Primary}, so by-interface injection here would
 * silently resolve to the wrong service). This chain never reads or writes
 * {@link com.vanter.ember.config.TenantContextHolder} — mutual exclusion between platform and
 * tenant auth comes entirely from the two disjoint signing keys, not a claim check: a tenant
 * token fails {@link PlatformJwtService#isTokenValid} and vice versa.
 */
@Configuration
@RequiredArgsConstructor
public class PlatformSecurityConfig {

    private final PlatformJwtService platformJwtService;
    private final PlatformOperatorDetailsService platformOperatorDetailsService;

    @Bean
    @Order(1)
    public SecurityFilterChain platformFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(new AntPathRequestMatcher("/platform/**"))
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/platform/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(platformUnauthorizedEntryPoint()))
                .addFilterBefore(platformJwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationEntryPoint platformUnauthorizedEntryPoint() {
        return (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public OncePerRequestFilter platformJwtAuthFilter() {
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
                if (!platformJwtService.isTokenValid(token)) {
                    chain.doFilter(request, response);
                    return;
                }
                String email = platformJwtService.extractSubject(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = platformOperatorDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                chain.doFilter(request, response);
            }
        };
    }
}
