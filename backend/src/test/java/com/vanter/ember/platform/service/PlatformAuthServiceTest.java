package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformAuthResponse;
import com.vanter.ember.platform.model.dto.PlatformLoginRequest;
import com.vanter.ember.platform.model.dto.PlatformPasswordChangeRequest;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

    private static final String EMAIL = "operator@ember.local";

    @Mock PlatformOperatorRepository platformOperatorRepository;
    @Mock PlatformJwtService platformJwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks PlatformAuthService platformAuthService;

    private PlatformOperator operator() {
        return PlatformOperator.builder()
                .id(UUID.randomUUID())
                .name("Platform Admin")
                .email(EMAIL)
                .passwordHash("hashed")
                .build();
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        PlatformOperator operator = operator();
        when(platformOperatorRepository.findByEmail(EMAIL)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("correct", operator.getPasswordHash())).thenReturn(true);
        when(platformJwtService.generateToken(eq(EMAIL), any())).thenReturn("jwt-token");

        PlatformLoginRequest request = new PlatformLoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("correct");

        PlatformAuthResponse response = platformAuthService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getOperatorId()).isEqualTo(operator.getId());
        assertThat(response.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void login_throwsForUnknownEmail() {
        when(platformOperatorRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        PlatformLoginRequest request = new PlatformLoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("whatever");

        assertThatThrownBy(() -> platformAuthService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsForWrongPassword() {
        PlatformOperator operator = operator();
        when(platformOperatorRepository.findByEmail(EMAIL)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("wrong", operator.getPasswordHash())).thenReturn(false);

        PlatformLoginRequest request = new PlatformLoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("wrong");

        assertThatThrownBy(() -> platformAuthService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePassword_updatesHashWhenCurrentPasswordMatches() {
        PlatformOperator operator = operator();
        when(platformOperatorRepository.findByEmail(EMAIL)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("current", operator.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newSecret")).thenReturn("new-hash");

        PlatformPasswordChangeRequest request = new PlatformPasswordChangeRequest();
        request.setCurrentPassword("current");
        request.setNewPassword("newSecret");

        platformAuthService.changePassword(EMAIL, request);

        assertThat(operator.getPasswordHash()).isEqualTo("new-hash");
        verify(platformOperatorRepository).save(operator);
    }

    @Test
    void changePassword_throwsWhenCurrentPasswordWrong() {
        PlatformOperator operator = operator();
        when(platformOperatorRepository.findByEmail(EMAIL)).thenReturn(Optional.of(operator));
        when(passwordEncoder.matches("wrong", operator.getPasswordHash())).thenReturn(false);

        PlatformPasswordChangeRequest request = new PlatformPasswordChangeRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("newSecret");

        assertThatThrownBy(() -> platformAuthService.changePassword(EMAIL, request))
                .isInstanceOf(BadCredentialsException.class);
    }

}
