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
      @if (submittedEmail(); as email) {
        <h1 id="register-title">Confira seu e-mail</h1>
        <p role="status">
          Enviamos um link de confirmação para <strong>{{ email }}</strong
          >. Abra o link para ativar sua conta. Ele expira em 30 minutos.
        </p>
        <p><a routerLink="/login">Já confirmou? Entrar</a></p>
      } @else {
        <h1 id="register-title">Criar conta opcional</h1>
        <p>
          Você pode comprar sem criar uma conta. Com uma conta, acompanha seus pedidos por aqui.
        </p>
        <form (ngSubmit)="submit()" #form="ngForm" novalidate>
          <label for="register-email">E-mail</label>
          <input
            id="register-email"
            name="email"
            type="email"
            [(ngModel)]="email"
            required
            email
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
            aria-describedby="password-hint"
          />
          <small id="password-hint">Use de 8 a 72 caracteres.</small>

          @if (error()) {
            <p role="alert" class="error">{{ error() }}</p>
          }

          <button type="submit" [disabled]="form.invalid || loading()">
            {{ loading() ? 'Enviando…' : 'Criar conta' }}
          </button>
        </form>
        <p>Já tem conta? <a routerLink="/login">Entrar</a></p>
        <p><a routerLink="/">Continuar sem conta</a></p>
      }
    </main>
  `,
  styles: `
    .register {
      max-width: 30rem;
      margin: 2rem auto;
      padding: 1.5rem;
    }
    h1 {
      color: var(--color-ink);
    }
    label {
      display: block;
      margin-top: 1rem;
    }
    input {
      box-sizing: border-box;
      width: 100%;
      padding: 0.7rem;
      margin-top: 0.25rem;
    }
    small {
      display: block;
      margin-top: 0.35rem;
    }
    button {
      width: 100%;
      padding: 0.8rem;
      margin-top: 1.25rem;
    }
    .error {
      color: #b91c1c;
    }
  `,
})
export class RegisterComponent {
  email = '';
  password = '';
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly submittedEmail = signal<string | null>(null);

  private readonly identity = inject(IdentityService);

  async submit(): Promise<void> {
    if (!this.email.trim() || this.password.length < 8 || this.password.length > 72) return;
    this.error.set(null);
    this.loading.set(true);
    try {
      const account = await this.identity.register(this.email.trim(), this.password);
      this.submittedEmail.set(account.email);
      this.password = '';
    } catch (error: unknown) {
      this.error.set(this.messageFor(error));
    } finally {
      this.loading.set(false);
    }
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 409) return 'Este e-mail já está cadastrado. Entre com sua conta.';
      if (error.status === 503)
        return 'Não foi possível enviar a confirmação. Tente novamente mais tarde.';
      if (error.status === 0 || error.status >= 500) {
        return 'Serviço indisponível no momento. Tente novamente mais tarde.';
      }
    }
    return 'Não foi possível criar a conta. Confira os dados e tente novamente.';
  }
}
