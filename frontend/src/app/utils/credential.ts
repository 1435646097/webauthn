import { base64UrlToBuffer, bufferToBase64Url } from './buffer';

export interface CreationOptionsJSON {
  challenge: string;
  rp: PublicKeyCredentialRpEntity;
  user: {
    id: string;
    name: string;
    displayName: string;
  };
  pubKeyCredParams: PublicKeyCredentialParameters[];
  timeout?: number;
  excludeCredentials?: Array<PublicKeyCredentialDescriptor & { id: string }>;
  authenticatorSelection?: AuthenticatorSelectionCriteria;
}

export interface RequestOptionsJSON {
  challenge: string;
  rpId?: string;
  timeout?: number;
  allowCredentials?: Array<PublicKeyCredentialDescriptor & { id: string }>;
  userVerification?: UserVerificationRequirement;
}

export interface AttestationCredentialJSON {
  id: string;
  rawId: string;
  type: PublicKeyCredentialType;
  clientExtensionResults: AuthenticationExtensionsClientOutputs;
  response: {
    attestationObject: string;
    clientDataJSON: string;
  };
}

export interface AssertionCredentialJSON {
  id: string;
  rawId: string;
  type: PublicKeyCredentialType;
  clientExtensionResults: AuthenticationExtensionsClientOutputs;
  response: {
    authenticatorData: string;
    clientDataJSON: string;
    signature: string;
    userHandle?: string | null;
  };
}

export function preformatCreationOptions(options: CreationOptionsJSON): PublicKeyCredentialCreationOptions {
  const publicKey: PublicKeyCredentialCreationOptions = {
    ...options,
    challenge: base64UrlToBuffer(options.challenge),
    user: {
      ...options.user,
      id: base64UrlToBuffer(options.user.id)
    },
    excludeCredentials: options.excludeCredentials?.map(descriptor => {
      const record: PublicKeyCredentialDescriptor = {
        type: descriptor.type,
        id: base64UrlToBuffer(descriptor.id)
      };
      if (descriptor.transports?.length) {
        record.transports = descriptor.transports;
      }
      return record;
    })
  };

  return sanitizeExtensions(publicKey);
}

export function preformatRequestOptions(options: RequestOptionsJSON): PublicKeyCredentialRequestOptions {
  const publicKey: PublicKeyCredentialRequestOptions = {
    ...options,
    challenge: base64UrlToBuffer(options.challenge),
    allowCredentials: options.allowCredentials?.map(descriptor => {
      const record: PublicKeyCredentialDescriptor = {
        type: descriptor.type,
        id: base64UrlToBuffer(descriptor.id)
      };
      if (descriptor.transports?.length) {
        record.transports = descriptor.transports;
      }
      return record;
    })
  };

  return sanitizeExtensions(publicKey);
}

export function attestationToJSON(credential: PublicKeyCredential): AttestationCredentialJSON {
  const response = credential.response as AuthenticatorAttestationResponse;
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type as PublicKeyCredentialType,
    clientExtensionResults: credential.getClientExtensionResults(),
    response: {
      attestationObject: bufferToBase64Url(response.attestationObject),
      clientDataJSON: bufferToBase64Url(response.clientDataJSON)
    }
  };
}

export function assertionToJSON(credential: PublicKeyCredential): AssertionCredentialJSON {
  const response = credential.response as AuthenticatorAssertionResponse;
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type as PublicKeyCredentialType,
    clientExtensionResults: credential.getClientExtensionResults(),
    response: {
      authenticatorData: bufferToBase64Url(response.authenticatorData),
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      signature: bufferToBase64Url(response.signature),
      userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null
    }
  };
}

function sanitizeExtensions<T extends { extensions?: AuthenticationExtensionsClientInputs }>(options: T): T {
  if (!options.extensions) {
    return options;
  }

  const extensions = options.extensions as Record<string, unknown>;
  for (const key of Object.keys(extensions)) {
    const value = extensions[key];
    if (value == null) {
      delete extensions[key];
      continue;
    }
    if (key === 'appid') {
      if (typeof value !== 'string' || !isValidUrl(value)) {
        delete extensions[key];
      }
    }
  }

  if (Object.keys(extensions).length === 0) {
    delete options.extensions;
  }

  return options;
}

function isValidUrl(value: string): boolean {
  try {
    new URL(value);
    return true;
  } catch {
    return false;
  }
}
