import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

@Component({
  selector: 'app-recover-access',
  imports: [FormsModule, RouterLink],
  template: `
    <main class="recovery" aria-labelledby="recovery-title">
      <h1 id="recovery-title">Recuperar acesso</h1>
      @if (requested()) {
        <p role="status" aria-live="polite">
          Se houver uma conta verificada para esse endereço, enviaremos instruções de recuperação.
        </p>
        <a routerLink="/login">Voltar ao login</a>
      } @else {
        <p>Informe o e-mail da conta. A resposta será a mesma para todos os endereços.</p>
        <form (ngSubmit)="submit()" novalidate>
          <label for="recovery-email">E-mail</label>
          <input
            id="recovery-email"
            name="email"
            type="email"
            [(ngModel)]="email"
            required
            maxlength="254"
            autocomplete="email"
            aria-required="true"
          />
          @if (error()) {
            <p role="alert" class="error">{{ error() }}</p>
          }
          <button type="submit" [disabled]="saving()">
            {{ saving() ? 'Enviando...' : 'Enviar instruções' }}
          </button>
        </form>
        <p><a routerLink="/login">Voltar ao login</a></p>
      }
    </main>
  `,
  styles: `
    .recovery {
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
    button {
      width: 100%;
      margin-top: 1rem;
      padding: 0.75rem;
    }
    .error {
      color: #b91c1c;
    }
  `,
})
export class RecoverAccessComponent {
  email = '';
  readonly saving = signal(false);
  readonly requested = signal(false);
  readonly error = signal<string | null>(null);

  private readonly identity = inject(IdentityService);

  async submit(): Promise<void> {
    this.error.set(null);
    if (!this.email.trim()) {
      this.error.set('Informe o e-mail da conta.');
      return;
    }
    this.saving.set(true);
    try {
      await this.identity.requestRecovery(this.email.trim());
      this.requested.set(true);
    } catch (error: unknown) {
      this.error.set(
        error instanceof HttpErrorResponse && error.status === 429
          ? 'Muitas solicitações recentes. Tente novamente depois.'
          : 'Não foi possível enviar as instruções agora. Tente novamente.',
      );
    } finally {
      this.saving.set(false);
    }
  }
}
