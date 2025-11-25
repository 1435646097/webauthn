package com.example.webauthn.service;

import com.example.webauthn.model.*;
import com.example.webauthn.repository.InMemoryCredentialRepository;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebAuthnService {

    private final RelyingParty relyingParty;
    private final InMemoryCredentialRepository credentialRepository;
    private final Map<String, PublicKeyCredentialCreationOptions> registrationRequests = new ConcurrentHashMap<>();
    private final Map<String, AssertionRequest> assertionRequests = new ConcurrentHashMap<>();

    public WebAuthnService(RelyingParty relyingParty, InMemoryCredentialRepository credentialRepository) {
        this.relyingParty = relyingParty;
        this.credentialRepository = credentialRepository;
    }

    public PublicKeyCredentialCreationOptions startRegistration(RegistrationStartRequest request) {
        Assert.hasText(request.username(), "用户名不能为空");
        UserAccount user = credentialRepository.ensureUser(request.username(), request.displayName());

        AuthenticatorSelectionCriteria selectionCriteria = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .residentKey(request.residentKey() ? ResidentKeyRequirement.REQUIRED : ResidentKeyRequirement.PREFERRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(user.getUsername())
                                .displayName(user.getDisplayName())
                                .id(user.getUserHandle())
                                .build())
                        .authenticatorSelection(selectionCriteria)
                        .build()
        );

        registrationRequests.put(user.getUsername(), options);
        return options;
    }

    public RegistrationFinishResponse finishRegistration(RegistrationFinishRequest request) throws RegistrationFailedException {
        PublicKeyCredentialCreationOptions creationOptions = registrationRequests.remove(request.username());
        if (creationOptions == null) {
            throw new IllegalStateException("注册挑战已过期或不存在");
        }
        RegistrationResult result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                        .request(creationOptions)
                        .response(request.credential())
                        .build());

        UserAccount user = credentialRepository.ensureUser(request.username(), request.username());
        RegisteredCredential credential = RegisteredCredential.builder()
                .credentialId(result.getKeyId().getId())
                .userHandle(user.getUserHandle())
                .publicKeyCose(result.getPublicKeyCose())
                .signatureCount(result.getSignatureCount())
                .build();
        credentialRepository.addCredential(request.username(), credential);

        return new RegistrationFinishResponse(true, "注册成功");
    }

    public PublicKeyCredentialRequestOptions startAuthentication(AuthenticationStartRequest request) {
        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(Optional.ofNullable(request.username()))
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build());
        if (request.username() != null && !request.username().isBlank()) {
            assertionRequests.put(request.username(), assertionRequest);
        } else {
            assertionRequests.put(assertionRequest.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url(), assertionRequest);
        }
        return assertionRequest.getPublicKeyCredentialRequestOptions();
    }

    public AuthenticationFinishResponse finishAuthentication(AuthenticationFinishRequest request) throws AssertionFailedException {
        AssertionRequest assertionRequest = Optional.ofNullable(assertionRequests.remove(request.username()))
                .orElseThrow(() -> new IllegalStateException("认证挑战已过期或不存在"));

        AssertionResult result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                        .request(assertionRequest)
                        .response(request.credential())
                        .build());

        credentialRepository.updateCredential(result.getUsername(), request.credential().getId(), result.getSignatureCount());

        return new AuthenticationFinishResponse(result.isSuccess(), result.getUsername(), result.getSignatureCount());
    }
}
