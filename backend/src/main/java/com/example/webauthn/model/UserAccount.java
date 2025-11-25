package com.example.webauthn.model;

import com.yubico.webauthn.data.ByteArray;

public class UserAccount {

    private final String username;
    private final String displayName;
    private final ByteArray userHandle;

    public UserAccount(String username, String displayName, ByteArray userHandle) {
        this.username = username;
        this.displayName = displayName;
        this.userHandle = userHandle;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ByteArray getUserHandle() {
        return userHandle;
    }
}
