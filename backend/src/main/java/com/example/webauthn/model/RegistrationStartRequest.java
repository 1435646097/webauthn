package com.example.webauthn.model;

import jakarta.validation.constraints.NotBlank;

public record RegistrationStartRequest(
        @NotBlank String username,
        @NotBlank String displayName,
        boolean residentKey
) {
}
