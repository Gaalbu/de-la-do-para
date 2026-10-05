import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="register" aria-labelledby="register-title">
      <h1 id="register-title">Criar conta</h1>
      <p>Ter uma conta é opcional. Você continua podendo comprar como convidado.</p>
      @if (registered()) {
        <section role="status" aria-live="polite">
          <h2>Cadastro recebido</h2>
          <p>Enviaremos um link de confirmação para o endereço informado.</p>
          <p>Você pode comprar como convidado enquanto isso.</p>
          <a routerLink="/login">Ir para entrar</a>
        </section>
      } @else {
        <form (ngSubmit)="submit()" #form="ngForm" novalidate>
          <label for="register-email">E-mail</label>
          <input
            id="register-email"
            name="email"
            type="email"
            [(ngModel)]="email"
            required
            maxlength="254"
            autocomplete="email"
            aria-required="true"
          />

          <label for="register-password">Senha</label>
          <input
            id="register-password"
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
            {{ saving() ? 'Enviando...' : 'Criar conta' }}
          </button>
        </form>
        <p>Já tem conta? <a routerLink="/login">Entrar</a></p>
      }
    </main>
  `,
  styles: `
    .register {
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
      margin-top: 0.35rem;
      color: #405b50;
      font-size: 0.9rem;
    }
    .error {
      color: #b91c1c;
      margin-top: 0.75rem;
    }
    button {
      width: 100%;
      margin-top: 1rem;
      padding: 0.75rem;
    }
  `,
})
export class RegisterComponent {
  email = '';
  password = '';
  readonly saving = signal(false);
  readonly registered = signal(false);
  readonly error = signal<string | null>(null);

  private readonly identity = inject(IdentityService);

  async submit(): Promise<void> {
    this.error.set(null);
    if (!this.email.trim() || this.password.length < 8 || this.password.length > 72) {
      this.error.set('Informe um e-mail válido e uma senha entre 8 e 72 caracteres.');
      return;
    }
    this.saving.set(true);
    try {
      await this.identity.register({ email: this.email.trim(), password: this.password });
      this.registered.set(true);
      this.password = '';
    } catch (error: unknown) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      return 'Este e-mail já está cadastrado. Entre ou recupere o acesso.';
    }
    if (error instanceof HttpErrorResponse && error.status === 400) {
      return 'Confira o e-mail e a senha informados.';
    }
    return 'Não foi possível criar a conta agora. Tente novamente.';
  }
}
