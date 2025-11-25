import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AttestationCredentialJSON,
  AssertionCredentialJSON,
  CreationOptionsJSON,
  RequestOptionsJSON
} from './utils/credential';

export interface RegistrationStartPayload {
  username: string;
  displayName: string;
  residentKey: boolean;
}

export interface RegistrationFinishPayload {
  username: string;
  credential: AttestationCredentialJSON;
}

export interface AuthenticationStartPayload {
  username: string;
}

export interface AuthenticationFinishPayload {
  username: string;
  credential: AssertionCredentialJSON;
}

export interface RegistrationFinishResponse {
  success: boolean;
  message: string;
}

export interface AuthenticationFinishResponse {
  success: boolean;
  username: string;
  signatureCount: number;
}

@Injectable({ providedIn: 'root' })
export class WebauthnService {
  private readonly http = inject(HttpClient);

  startRegistration(payload: RegistrationStartPayload): Observable<CreationOptionsJSON> {
    return this.http.post<CreationOptionsJSON>('/api/webauthn/register/options', payload);
  }

  finishRegistration(payload: RegistrationFinishPayload): Observable<RegistrationFinishResponse> {
    return this.http.post<RegistrationFinishResponse>('/api/webauthn/register/finish', payload);
  }

  startAuthentication(payload: AuthenticationStartPayload): Observable<RequestOptionsJSON> {
    return this.http.post<RequestOptionsJSON>('/api/webauthn/authenticate/options', payload);
  }

  finishAuthentication(payload: AuthenticationFinishPayload): Observable<AuthenticationFinishResponse> {
    return this.http.post<AuthenticationFinishResponse>('/api/webauthn/authenticate/finish', payload);
  }
}
