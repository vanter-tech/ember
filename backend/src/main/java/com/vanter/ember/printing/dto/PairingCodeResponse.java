package com.vanter.ember.printing.dto;

import java.time.LocalDateTime;

public record PairingCodeResponse(String code, LocalDateTime expiresAt) {}
