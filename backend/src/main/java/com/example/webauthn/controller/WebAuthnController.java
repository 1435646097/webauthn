package com.example.webauthn.controller;

import com.example.webauthn.model.AuthenticationFinishRequest;
import com.example.webauthn.model.AuthenticationFinishResponse;
import com.example.webauthn.model.AuthenticationStartRequest;
import com.example.webauthn.model.RegistrationFinishRequest;
import com.example.webauthn.model.RegistrationFinishResponse;
import com.example.webauthn.model.RegistrationStartRequest;
import com.example.webauthn.service.WebAuthnService;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webauthn")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    @PostMapping("/register/options")
    public ResponseEntity<PublicKeyCredentialCreationOptions> startRegistration(
            @Valid @RequestBody RegistrationStartRequest request) {
        return ResponseEntity.ok(webAuthnService.startRegistration(request));
    }

    @PostMapping("/register/finish")
    public ResponseEntity<RegistrationFinishResponse> finishRegistration(
            @Valid @RequestBody RegistrationFinishRequest request) throws RegistrationFailedException {
        return ResponseEntity.ok(webAuthnService.finishRegistration(request));
    }

    @PostMapping("/authenticate/options")
    public ResponseEntity<PublicKeyCredentialRequestOptions> startAuthentication(
            @Valid @RequestBody AuthenticationStartRequest request) {
        return ResponseEntity.ok(webAuthnService.startAuthentication(request));
    }

    @PostMapping("/authenticate/finish")
    public ResponseEntity<AuthenticationFinishResponse> finishAuthentication(
            @Valid @RequestBody AuthenticationFinishRequest request) throws AssertionFailedException {
        return ResponseEntity.ok(webAuthnService.finishAuthentication(request));
    }
}
