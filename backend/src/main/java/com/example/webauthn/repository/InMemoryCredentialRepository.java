package com.example.webauthn.repository;

import com.example.webauthn.model.UserAccount;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCredentialRepository implements CredentialRepository {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final Map<String, List<RegisteredCredential>> credentials = new ConcurrentHashMap<>();

    public UserAccount ensureUser(String username, String displayName) {
        return users.computeIfAbsent(username, name ->
                new UserAccount(name, displayName, randomHandle()));
    }

    public void addCredential(String username, RegisteredCredential credential) {
        credentials.computeIfAbsent(username, key -> new ArrayList<>()).add(credential);
    }

    public void updateCredential(String username, ByteArray credentialId, long signatureCount) {
        List<RegisteredCredential> registeredCredentials = credentials.get(username);
        if (registeredCredentials == null) {
            return;
        }
        for (int i = 0; i < registeredCredentials.size(); i++) {
            RegisteredCredential credential = registeredCredentials.get(i);
            if (credential.getCredentialId().equals(credentialId)) {
                registeredCredentials.set(i, RegisteredCredential.builder()
                        .credentialId(credential.getCredentialId())
                        .userHandle(credential.getUserHandle())
                        .publicKeyCose(credential.getPublicKeyCose())
                        .signatureCount(signatureCount)
                        .build());
                break;
            }
        }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        List<RegisteredCredential> registeredCredentials = credentials.getOrDefault(username, Collections.emptyList());
        return registeredCredentials.stream()
                .map(cred -> PublicKeyCredentialDescriptor.builder()
                        .id(cred.getCredentialId())
                        .build())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return Optional.ofNullable(users.get(username)).map(UserAccount::getUserHandle);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return users.values().stream()
                .filter(user -> user.getUserHandle().equals(userHandle))
                .findFirst()
                .map(UserAccount::getUsername);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentials.values().stream()
                .flatMap(List::stream)
                .filter(cred -> cred.getCredentialId().equals(credentialId)
                        && cred.getUserHandle().equals(userHandle))
                .findFirst();
    }


    public Optional<RegisteredCredential> lookup(ByteArray credentialId) {
        return credentials.values().stream()
                .flatMap(List::stream)
                .filter(cred -> cred.getCredentialId().equals(credentialId))
                .findFirst();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentials.values().stream()
                .flatMap(List::stream)
                .filter(cred -> cred.getCredentialId().equals(credentialId))
                .collect(java.util.stream.Collectors.toSet());
    }

    public List<RegisteredCredential> getCredentials(String username) {
        return credentials.getOrDefault(username, Collections.emptyList());
    }

    private ByteArray randomHandle() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return new ByteArray(buffer);
    }
}
