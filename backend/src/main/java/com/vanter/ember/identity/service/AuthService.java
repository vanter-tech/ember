package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantService restaurantService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        Restaurant restaurant = restaurantService.getBySlug(request.getRestaurantSlug());

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getEmail(),
                Map.of("role", user.getRole().name(), "userId", user.getId(), "rid", restaurant.getId())
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .restaurantId(restaurant.getId())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Restaurant restaurant = resolveLoginRestaurant(user, request.getRestaurantSlug());

        String token = jwtService.generateToken(
                user.getEmail(),
                Map.of("role", user.getRole().name(), "userId", user.getId(),
                        "rid", restaurant.getId())
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole().name())
                .restaurantId(restaurant.getId())
                .build();
    }

    /**
     * CUSTOMER accounts aren't bound to one restaurant, so their "current" tenant comes from the
     * QR/table-code landing page they logged in from, not from a stored field. ADMIN/WAITER
     * accounts are tenant-bound at creation, so they keep using {@code User.restaurantId}.
     */
    private Restaurant resolveLoginRestaurant(User user, String restaurantSlug) {
        if (user.getRole() != Role.CUSTOMER) {
            return user.getRestaurantId();
        }
        if (!StringUtils.hasText(restaurantSlug)) {
            throw new IllegalArgumentException("restaurantSlug is required for customer login");
        }
        return restaurantService.getBySlug(restaurantSlug);
    }
}
