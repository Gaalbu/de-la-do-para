import { isPlatformBrowser } from '@angular/common';
import { Component, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink],
  template: `
    <main class="verification" aria-labelledby="verification-title">
      <h1 id="verification-title">Confirmar e-mail</h1>
      @if (working()) {
        <p role="status">Confirmando seu endereço…</p>
      } @else if (verified()) {
        <p role="status" aria-live="polite">E-mail confirmado. Sua conta está pronta.</p>
        <a routerLink="/login">Entrar</a>
      } @else {
        <p role="alert">{{ message() }}</p>
        <p>Sua conta continua opcional para comprar como convidado.</p>
        <a routerLink="/login">Voltar ao login</a>
      }
    </main>
  `,
  styles: `
    .verification {
      max-width: 36rem;
      margin: 2rem auto;
      padding: 1rem;
    }
  `,
})
export class VerifyEmailComponent implements OnInit {
  readonly working = signal(true);
  readonly verified = signal(false);
  readonly message = signal(
    'Não foi possível confirmar o e-mail. Verifique o link e tente novamente.',
  );

  private readonly platformId = inject(PLATFORM_ID);
  private readonly identity = inject(IdentityService);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.working.set(false);
      return;
    }
    void this.verifyFromFragment();
  }

  private async verifyFromFragment(): Promise<void> {
    const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
    window.history.replaceState(
      window.history.state,
      '',
      `${window.location.pathname}${window.location.search}`,
    );
    if (!token) {
      this.working.set(false);
      this.message.set('O link de confirmação está ausente ou incompleto.');
      return;
    }
    try {
      const result = await this.identity.verifyEmail(token);
      this.verified.set(result.emailVerified);
      if (!result.emailVerified) {
        this.message.set('O servidor não confirmou este endereço. Confira o link mais recente.');
      }
    } catch {
      this.message.set(
        'O link expirou, já foi usado ou não é válido. Confira a mensagem mais recente.',
      );
    } finally {
      this.working.set(false);
    }
  }
}
