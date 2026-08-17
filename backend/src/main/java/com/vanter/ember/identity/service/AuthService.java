package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);

        return buildResponse(user, tenantIdOf(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return buildResponse(user, tenantIdOf(user));
    }

    /**
     * Re-issues a caller's token bound to the restaurant they just joined a table at. CUSTOMER
     * tokens start with no tenant (see {@link #tenantIdOf}); this is how they acquire one, and the
     * only caller is the session-join flow, which derives {@code restaurantId} from a server-signed
     * QR token or from the session document itself — never from raw client input.
     */
    public AuthResponse issueTenantScopedToken(String email, UUID restaurantId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        // Only CUSTOMERs float between restaurants. Staff stay pinned to their own tenant
        // whatever session they touched, so a stray join can never widen their access.
        UUID scoped = user.getRole() == Role.CUSTOMER ? restaurantId : tenantIdOf(user);
        return buildResponse(user, scoped);
    }

    /**
     * CUSTOMER accounts aren't bound to a restaurant: they can order at any tenant, and which one
     * only becomes known when they join a table. ADMIN/WAITER/KITCHEN staff are tenant-bound at
     * creation, so their tenant comes straight off the stored {@code User.restaurantId}.
     */
    private UUID tenantIdOf(User user) {
        if (user.getRole() == Role.CUSTOMER || user.getRestaurantId() == null) {
            return null;
        }
        return user.getRestaurantId().getId();
    }

    private AuthResponse buildResponse(User user, UUID restaurantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        if (restaurantId != null) {
            claims.put("rid", restaurantId);
        }

        return AuthResponse.builder()
                .token(jwtService.generateToken(user.getEmail(), claims))
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole().name())
                .restaurantId(restaurantId)
                .build();
    }
}
