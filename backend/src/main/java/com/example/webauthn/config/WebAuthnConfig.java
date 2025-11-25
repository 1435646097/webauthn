package com.example.webauthn.config;

import com.example.webauthn.repository.InMemoryCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(WebAuthnProperties properties, InMemoryCredentialRepository repository) {
        Set<String> origins = new HashSet<>(properties.getOrigins());
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(properties.getRpId())
                        .name(properties.getRpName())
                        .build())
                .credentialRepository(repository)
                .origins(origins)
                .build();
    }
}
