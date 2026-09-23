package com.vanter.ember.identity.service;

import com.vanter.ember.identity.exception.PinNotSetException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.PinLoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PinAttemptGuard pinAttemptGuard;
    private final RestaurantRepository restaurantRepository;

    /** False inside the Hub itself; see {@link DeploymentMode#isClosedToWeb}. */
    @Value("${ember.deployment-mode.enforced:true}")
    private boolean deploymentModeEnforced = true;

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

        if (!Boolean.TRUE.equals(user.getActive()) || closedToWeb(user)) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return buildResponse(user, tenantIdOf(user));
    }

    /**
     * F-10/E-23: this endpoint's 401 (unknown email) vs. 409 {@link PinNotSetException} vs. 423
     * {@link com.vanter.ember.identity.exception.PinLockedException} split is a deliberate, kept
     * product trade-off — {@code QuickLoginModal}'s UX needs the distinction — so it isn't
     * unified here. Rate-limited separately, tighter than the shared auth budget
     * ({@code AuthRateLimiterFilter}/{@code RateLimitProperties#pinLoginMaxRequests}); the
     * {@code log.warn} calls below only add visibility so a scripted enumeration attempt shows up
     * in logs instead of being silent — they never change what the caller sees.
     */
    public AuthResponse loginWithPin(PinLoginRequest request) {
        try {
            pinAttemptGuard.assertNotLocked(request.getEmail());
        } catch (com.vanter.ember.identity.exception.PinLockedException e) {
            log.warn("PIN login rejected: account locked for {}", request.getEmail());
            throw e;
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("PIN login rejected: unknown email {}", request.getEmail());
                    return new BadCredentialsException("Invalid credentials");
                });

        if (user.getPinHash() == null) {
            log.warn("PIN login rejected: no PIN set for {}", request.getEmail());
            throw new PinNotSetException();
        }

        boolean pinMatches = passwordEncoder.matches(request.getPin(), user.getPinHash());
        if (!pinMatches || !Boolean.TRUE.equals(user.getActive()) || closedToWeb(user)) {
            pinAttemptGuard.recordFailure(request.getEmail());
            if (!pinMatches) {
                log.warn("PIN login rejected: wrong PIN for {}", request.getEmail());
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        pinAttemptGuard.recordSuccess(request.getEmail());
        return buildResponse(user, tenantIdOf(user));
    }

    /**
     * A Hub restaurant's staff have no business on Ember Web. Staff only: CUSTOMER accounts float
     * between restaurants and are gated when they join a table.
     */
    private boolean closedToWeb(User user) {
        if (!deploymentModeEnforced || user.getRole() == Role.CUSTOMER || user.getRestaurantId() == null) {
            return false;
        }
        // User.restaurantId is a LAZY proxy: only its id is readable outside a transaction, so the
        // mode has to come from the repository (same lookup jwtAuthFilter does per request).
        Restaurant restaurant = restaurantRepository.findById(user.getRestaurantId().getId()).orElse(null);
        return DeploymentMode.isClosedToWeb(restaurant, deploymentModeEnforced);
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
        claims.put("ver", user.getTokenVersion());
        if (restaurantId != null) {
            claims.put("rid", restaurantId);
        }

        return AuthResponse.builder()
                .token(jwtService.generateToken(user.getEmail(), claims))
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole().name())
                .restaurantId(restaurantId)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }

    /**
     * Self-service password change (F-25). The only way a user can clear {@code
     * mustChangePassword} after a platform-operator reset — also usable as an ordinary voluntary
     * change. Requires the current password like any self-service credential change; bumps {@code
     * tokenVersion} so older sessions stop working, and returns a fresh token bound to the new
     * version so the caller doesn't get logged out by their own request.
     */
    public AuthResponse changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        return buildResponse(user, tenantIdOf(user));
    }
}
