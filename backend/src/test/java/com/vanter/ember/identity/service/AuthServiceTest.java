package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RestaurantService restaurantService;
    @InjectMocks AuthService authService;

    @Test
    void register_savesUserAndReturnsToken() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("secret");
        req.setRestaurantSlug("anas-diner");

        Restaurant restaurant = Restaurant.builder().id(UUID.randomUUID()).name("Ana's Diner").build();

        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(restaurantService.getBySlug("anas-diner")).thenReturn(restaurant);
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            return u;
        });
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.register(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getName()).isEqualTo("Ana");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@test.com");
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void login_resolvesRestaurantFromSlugForCustomer() {
        Restaurant restaurant = Restaurant.builder().id(UUID.randomUUID()).name("Ana's Diner").build();
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");
        req.setRestaurantSlug("anas-diner");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(restaurantService.getBySlug("anas-diner")).thenReturn(restaurant);
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
    }

    @Test
    void login_throwsWhenCustomerOmitsRestaurantSlug() {
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_usesStoredRestaurantForNonCustomerRoles() {
        Restaurant restaurant = Restaurant.builder().id(UUID.randomUUID()).name("HQ").build();
        User user = User.builder()
                .id("user-1").name("Walter").email("walter@test.com")
                .passwordHash("hashed").role(Role.WAITER).restaurantId(restaurant).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("walter@test.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("walter@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("walter@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getRestaurantId()).isEqualTo(restaurant.getId());
    }

    @Test
    void login_throwsForUnknownEmail() {
        LoginRequest req = new LoginRequest();
        req.setEmail("unknown@test.com");
        req.setPassword("any");

        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsForWrongPassword() {
        User user = User.builder()
                .email("ana@test.com").passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }
}
