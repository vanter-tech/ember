package com.vanter.ember.platform.service;

import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformAuthResponse;
import com.vanter.ember.platform.model.dto.PlatformLoginRequest;
import com.vanter.ember.platform.model.dto.PlatformPasswordChangeRequest;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Self-service auth for {@link PlatformOperator}s (EMB-PC-05), separate from
 * {@link com.vanter.ember.identity.service.AuthService}: tokens are minted by
 * {@link PlatformJwtService} under {@code platform.jwt.secret}, and there is exactly one operator
 * "role" ({@code PLATFORM_ADMIN}), matching {@link PlatformOperatorDetailsService}'s fixed
 * authority.
 */
@Service
@RequiredArgsConstructor
public class PlatformAuthService {

    private final PlatformOperatorRepository platformOperatorRepository;
    private final PlatformJwtService platformJwtService;
    private final PasswordEncoder passwordEncoder;

    public PlatformAuthResponse login(PlatformLoginRequest request) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), operator.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "PLATFORM_ADMIN");
        claims.put("operatorId", operator.getId());

        return PlatformAuthResponse.builder()
                .token(platformJwtService.generateToken(operator.getEmail(), claims))
                .operatorId(operator.getId())
                .name(operator.getName())
                .email(operator.getEmail())
                .build();
    }

    public void changePassword(String email, PlatformPasswordChangeRequest request) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), operator.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        operator.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        platformOperatorRepository.save(operator);
    }
}
