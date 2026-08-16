package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @InjectMocks AuthService authService;

    @Test
    void register_savesCustomerWithNoRestaurantAndReturnsToken() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
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
        // A customer isn't tied to a restaurant until they join a table.
        assertThat(response.getRestaurantId()).isNull();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRestaurantId()).isNull();
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
    void login_leavesCustomerTokenTenantlessEvenWithAStoredRestaurant() {
        // Legacy rows still carry a restaurant from before customers were decoupled; it must not
        // leak back into the token, or the customer would be pinned to that one restaurant again.
        Restaurant legacy = Restaurant.builder().id(UUID.randomUUID()).name("Ana's Diner").build();
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).restaurantId(legacy).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getRestaurantId()).isNull();
    }

    @Test
    void login_customerWithoutARestaurantSucceeds() {
        // The account that used to 500: no restaurant row at all.
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        assertThat(authService.login(req).getToken()).isEqualTo("jwt-token");
    }

    @Test
    void issueTenantScopedToken_bindsCustomerToTheRestaurantTheyJoined() {
        UUID joined = UUID.randomUUID();
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("scoped-token");

        AuthResponse response = authService.issueTenantScopedToken("ana@test.com", joined);

        assertThat(response.getToken()).isEqualTo("scoped-token");
        assertThat(response.getRestaurantId()).isEqualTo(joined);
    }

    @Test
    void issueTenantScopedToken_refusesToMoveStaffToAnotherRestaurant() {
        Restaurant own = Restaurant.builder().id(UUID.randomUUID()).name("HQ").build();
        User waiter = User.builder()
                .id("user-2").name("Walter").email("walter@test.com")
                .passwordHash("hashed").role(Role.WAITER).restaurantId(own).build();

        when(userRepository.findByEmail("walter@test.com")).thenReturn(Optional.of(waiter));
        when(jwtService.generateToken(eq("walter@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.issueTenantScopedToken(
                "walter@test.com", UUID.randomUUID());

        assertThat(response.getRestaurantId()).isEqualTo(own.getId());
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
