package com.vanter.ember.identity.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.UserProfileResponse;
import com.vanter.ember.identity.model.BannerKey;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getByEmail(String email) {
        return toResponse(require(email));
    }

    @Transactional
    public UserProfileResponse updateBanner(String email, BannerKey bannerKey) {
        User user = require(email);
        user.setBannerKey(bannerKey);
        return toResponse(userRepository.save(user));
    }

    private User require(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private UserProfileResponse toResponse(User u) {
        return new UserProfileResponse(u.getName(), u.getEmail(), u.getBannerKey());
    }
}
