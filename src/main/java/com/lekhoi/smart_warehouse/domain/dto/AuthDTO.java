package com.lekhoi.smart_warehouse.domain.dto;

import jakarta.validation.constraints.NotBlank;

public interface AuthDTO {

    record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    record LoginResponse(
        String accessToken,
        AccountDTO.Response account
    ) {}
}
