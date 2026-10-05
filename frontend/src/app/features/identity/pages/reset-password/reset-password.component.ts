import { isPlatformBrowser } from '@angular/common';
import { Component, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="reset" aria-labelledby="reset-title">
      <h1 id="reset-title">Redefinir senha</h1>
      @if (loadingToken()) {
        <p role="status">Verificando o link…</p>
      } @else if (changed()) {
        <p role="status" aria-live="polite">Senha atualizada. Entre novamente para continuar.</p>
        <a routerLink="/login">Entrar</a>
      } @else if (tokenAvailable()) {
        <form (ngSubmit)="submit()" novalidate>
          <label for="new-password">Nova senha</label>
          <input
            id="new-password"
            name="password"
            type="password"
            [(ngModel)]="password"
            required
            minlength="8"
            maxlength="72"
            autocomplete="new-password"
            aria-required="true"
          />
          <p class="hint">Use entre 8 e 72 caracteres.</p>
          @if (error()) {
            <p role="alert" class="error">{{ error() }}</p>
          }
          <button type="submit" [disabled]="saving()">
            {{ saving() ? 'Atualizando...' : 'Atualizar senha' }}
          </button>
        </form>
      } @else {
        <p role="alert">{{ error() }}</p>
        <a routerLink="/recover-access">Solicitar outro link</a>
      }
    </main>
  `,
  styles: `
    .reset {
      max-width: 30rem;
      margin: 2rem auto;
      padding: 1rem;
    }
    label {
      display: block;
      margin-top: 1rem;
    }
    input {
      box-sizing: border-box;
      width: 100%;
      padding: 0.65rem;
      margin-top: 0.25rem;
    }
    .hint {
      color: #405b50;
      font-size: 0.9rem;
    }
    .error {
      color: #b91c1c;
    }
    button {
      width: 100%;
      margin-top: 1rem;
      padding: 0.75rem;
    }
  `,
})
export class ResetPasswordComponent implements OnInit {
  password = '';
  readonly loadingToken = signal(true);
  readonly tokenAvailable = signal(false);
  readonly saving = signal(false);
  readonly changed = signal(false);
  readonly error = signal<string | null>(null);

  private readonly platformId = inject(PLATFORM_ID);
  private readonly identity = inject(IdentityService);
  private token: string | null = null;

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.loadingToken.set(false);
      this.error.set('Abra o link de recuperação em um navegador.');
      return;
    }
    this.token = new URLSearchParams(window.location.hash.slice(1)).get('token');
    window.history.replaceState(
      window.history.state,
      '',
      `${window.location.pathname}${window.location.search}`,
    );
    this.tokenAvailable.set(this.token !== null && this.token.length > 0);
    this.loadingToken.set(false);
    if (!this.tokenAvailable()) {
      this.error.set('O link expirou, já foi usado ou está incompleto.');
    }
  }

  async submit(): Promise<void> {
    this.error.set(null);
    if (!this.token || this.password.length < 8 || this.password.length > 72) {
      this.error.set('Informe uma senha entre 8 e 72 caracteres.');
      return;
    }
    this.saving.set(true);
    try {
      const result = await this.identity.resetPassword(this.token, this.password);
      this.token = null;
      this.password = '';
      this.changed.set(result.passwordChanged);
      this.tokenAvailable.set(false);
      if (!result.passwordChanged) {
        this.error.set('O link expirou ou já foi usado. Solicite outro para continuar.');
      }
    } catch {
      this.token = null;
      this.password = '';
      this.tokenAvailable.set(false);
      this.error.set(
        'O link expirou, já foi usado ou não é válido. Solicite outro para continuar.',
      );
    } finally {
      this.saving.set(false);
    }
  }
}
