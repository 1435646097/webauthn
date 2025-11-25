# Windows Hello WebAuthn Demo

This repository contains a Spring Boot 3 backend and an Angular 17 frontend that implement a Windows 11 Windows Hello (PIN) flow via the WebAuthn protocol.

## Layout

```
backend/   Spring Boot 3 WebAuthn REST API
frontend/  Angular 17 client that talks to the API
```

## Prerequisites

1. Windows Hello PIN must be configured on Windows 11.
2. Backend: Java 17+ and Maven 3.9+.
3. Frontend: Node.js 18+ (npm ships with Node).

## Run the backend

```powershell
cd backend
mvn spring-boot:run
```

The service listens on `http://localhost:8080`. Update `backend/src/main/resources/application.yml` (`webauthn.origins`) when deploying elsewhere.

## Run the Angular app

```powershell
cd frontend
npm install
npm start
```

The Angular dev server runs on `http://localhost:4200` and proxies `/api` to the backend through `proxy.conf.json`.

## Windows Hello flow

1. **Registration**
   - The UI collects username/display name and calls `/api/webauthn/register/options`.
   - The frontend converts the JSON challenge/user id to `ArrayBuffer`, enforces `authenticatorAttachment="platform"` and `userVerification="required"`, then calls `navigator.credentials.create`.
   - Windows Hello prompts for the PIN, returns a credential, and the frontend posts it to `/api/webauthn/register/finish`.
   - The backend verifies attestation via Yubico WebAuthn Server helpers and stores a `RegisteredCredential`.

2. **Authentication**
   - `/api/webauthn/authenticate/options` returns a challenge restricted to the user.
   - `navigator.credentials.get` is invoked with `userVerification="required"`, which triggers the Windows Hello PIN UI.
   - The backend verifies the assertion, bumps the signature counter, and returns success metadata.

## Key files

- `backend/src/main/java/com/example/webauthn/service/WebAuthnService.java`: orchestrates challenges, relies on the Yubico server library.
- `backend/src/main/java/com/example/webauthn/repository/InMemoryCredentialRepository.java`: demo credential store (swap with a database in production).
- `frontend/src/app/app.component.ts`: Angular UI that bridges navigator.credentials with the REST API.
- `frontend/src/app/utils/credential.ts`: helper functions that perform Base64URL and ArrayBuffer conversions.

## Production notes

- Replace the in-memory repository with persistent storage and tie challenges to user sessions.
- Harden the REST layer with authentication, CSRF mitigation, and rate limiting.
- WebAuthn requires HTTPS in production; only `localhost` works without TLS.
- Integrate your own login state (JWT/session) after a successful assertion result.
