import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import {
  attestationToJSON,
  assertionToJSON,
  preformatCreationOptions,
  preformatRequestOptions
} from './utils/credential';
import {
  WebauthnService,
  AuthenticationFinishPayload,
  AuthenticationStartPayload,
  RegistrationFinishPayload,
  RegistrationStartPayload
} from './webauthn.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(WebauthnService);

  readonly registerForm = this.fb.group({
    username: ['', Validators.required],
    displayName: ['', Validators.required],
    residentKey: [true]
  });

  readonly loginForm = this.fb.group({
    username: ['', Validators.required]
  });

  readonly logs = signal<string[]>([]);

  ngOnInit(): void {
    if (!('credentials' in navigator)) {
      this.appendLog('当前浏览器不支持 WebAuthn。');
    }
  }

  async register(): Promise<void> {
    if (this.registerForm.invalid) {
      this.appendLog('请完整填写注册信息');
      return;
    }
    try {
      const payload: RegistrationStartPayload = this.registerForm.getRawValue() as RegistrationStartPayload;
      const creationOptions = await firstValueFrom(this.service.startRegistration(payload));
      const publicKey = preformatCreationOptions(creationOptions);

      publicKey.authenticatorSelection = publicKey.authenticatorSelection || {};
      publicKey.authenticatorSelection.authenticatorAttachment = 'platform';
      publicKey.authenticatorSelection.userVerification = 'required';

      const credential = await navigator.credentials.create({ publicKey }) as PublicKeyCredential;
      if (!credential) {
        throw new Error('Windows Hello 没有返回凭据');
      }

      const finishPayload: RegistrationFinishPayload = {
        username: payload.username,
        credential: attestationToJSON(credential)
      };
      const result = await firstValueFrom(this.service.finishRegistration(finishPayload));
      this.appendLog(result.message);
    } catch (error) {
      console.error(error);
      this.appendLog(`注册失败: ${(error as Error).message}`);
    }
  }

  async login(): Promise<void> {
    if (this.loginForm.invalid) {
      this.appendLog('请输入用户名');
      return;
    }
    try {
      const payload: AuthenticationStartPayload = this.loginForm.getRawValue() as AuthenticationStartPayload;
      const requestOptions = await firstValueFrom(this.service.startAuthentication(payload));
      const publicKey = preformatRequestOptions(requestOptions);
      publicKey.userVerification = 'required';

      const assertion = await navigator.credentials.get({ publicKey }) as PublicKeyCredential;
      if (!assertion) {
        throw new Error('Windows Hello 没有返回断言');
      }

      const finishPayload: AuthenticationFinishPayload = {
        username: payload.username,
        credential: assertionToJSON(assertion)
      };
      const result = await firstValueFrom(this.service.finishAuthentication(finishPayload));
      if (result.success) {
        this.appendLog(`登录成功，最新签名计数：${result.signatureCount}`);
      } else {
        this.appendLog('登录失败，请重试');
      }
    } catch (error) {
      console.error(error);
      this.appendLog(`登录失败: ${(error as Error).message}`);
    }
  }

  private appendLog(message: string): void {
    this.logs.update(items => [message, ...items].slice(0, 10));
  }
}
