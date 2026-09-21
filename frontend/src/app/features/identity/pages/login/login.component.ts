import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="login" aria-labelledby="login-title">
      <h1 id="login-title">Entrar</h1>
      <form (ngSubmit)="onSubmit()" #form="ngForm" novalidate>
        <label for="email">E-mail</label>
        <input
          id="email"
          name="email"
          type="email"
          [(ngModel)]="email"
          required
          autocomplete="email"
          aria-required="true"
        />

        <label for="password">Senha</label>
        <input
          id="password"
          name="password"
          type="password"
          [(ngModel)]="password"
          required
          minlength="8"
          autocomplete="current-password"
          aria-required="true"
        />

        @if (error()) {
          <p role="alert" class="error">{{ error() }}</p>
        }

        <button type="submit" [disabled]="loading()">
          {{ loading() ? 'Entrando...' : 'Entrar' }}
        </button>
      </form>
      <p><a routerLink="/">Voltar à vitrine</a></p>
    </section>
  `,
  styles: `
    .login {
      max-width: 28rem;
      margin: 2rem auto;
      padding: 1rem;
    }
    label {
      display: block;
      margin-top: 1rem;
    }
    input {
      width: 100%;
      padding: 0.5rem;
      margin-top: 0.25rem;
    }
    .error {
      color: #b91c1c;
      margin-top: 0.75rem;
    }
    button {
      margin-top: 1rem;
      width: 100%;
      padding: 0.75rem;
    }
  `,
})
export class LoginComponent {
  email = '';
  password = '';
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  private readonly identity = inject(IdentityService);
  private readonly router = inject(Router);

  async onSubmit(): Promise<void> {
    this.error.set(null);
    if (!this.email || !this.password) {
      this.error.set('Preencha e-mail e senha.');
      return;
    }
    this.loading.set(true);
    try {
      const account = await this.identity.login(this.email, this.password);
      if (account.role === 'ADMIN') {
        await this.router.navigateByUrl('/admin');
      } else {
        await this.router.navigateByUrl('/');
      }
    } catch (err: unknown) {
      const message = err instanceof Error && err.message ? err.message : 'Credenciais inválidas.';
      this.error.set(message);
    } finally {
      this.loading.set(false);
    }
  }
}
