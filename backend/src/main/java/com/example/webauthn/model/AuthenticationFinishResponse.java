package com.example.webauthn.model;

public record AuthenticationFinishResponse(boolean success, String username, long signatureCount) {
}
