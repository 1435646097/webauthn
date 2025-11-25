package com.example.webauthn.model;

import jakarta.validation.constraints.NotBlank;

public record AuthenticationStartRequest(@NotBlank String username) {
}
