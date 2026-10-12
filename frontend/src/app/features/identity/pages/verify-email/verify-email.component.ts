import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IdentityService } from '../../services/identity.service';

type VerificationState = 'loading' | 'success' | 'missing' | 'invalid' | 'unavailable';

@Component({
  selector: 'app-verify-email',
  template: `
    <main class="verification" aria-labelledby="verification-title">
      <h1 id="verification-title">Confirmação de e-mail</h1>
      @switch (state()) {
        @case ('loading') {
          <p role="status">Confirmando seu e-mail…</p>
        }
        @case ('success') {
          <p role="status">E-mail confirmado. Sua conta já pode ser usada.</p>
          <a href="/login">Entrar</a>
        }
        @case ('missing') {
          <p role="alert">
            Este link não contém um código de confirmação. Abra o link recebido por e-mail.
          </p>
        }
        @case ('invalid') {
          <p role="alert">
            Este link é inválido, expirou ou já foi usado. Solicite uma nova confirmação.
          </p>
        }
        @case ('unavailable') {
          <p role="alert">Não foi possível confirmar agora. Tente novamente mais tarde.</p>
        }
      }
    </main>
  `,
  styles: `
    .verification {
      max-width: 40rem;
      margin: 2rem auto;
      padding: 1.5rem;
    }
    h1 {
      color: var(--color-ink);
    }
  `,
})
export class VerifyEmailComponent implements OnInit {
  readonly state = signal<VerificationState>('loading');

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly identity = inject(IdentityService);
  private started = false;

  ngOnInit(): void {
    if (this.started) return;
    this.started = true;
    void this.verifyFromFragment();
  }

  private async verifyFromFragment(): Promise<void> {
    const fragment = this.route.snapshot.fragment;
    const token = fragment === null ? null : new URLSearchParams(fragment).get('token');
    if (!token) {
      this.state.set('missing');
      return;
    }

    let fragmentRemoved: boolean;
    try {
      fragmentRemoved = await this.router.navigateByUrl(this.router.url.split('#', 1)[0], {
        replaceUrl: true,
      });
    } catch {
      this.state.set('unavailable');
      return;
    }
    if (!fragmentRemoved) {
      this.state.set('unavailable');
      return;
    }

    try {
      await this.identity.verifyEmail(token);
      this.state.set('success');
    } catch (error: unknown) {
      this.state.set(this.stateFor(error));
    }
  }

  private stateFor(error: unknown): VerificationState {
    if (error instanceof HttpErrorResponse && error.status === 410) return 'invalid';
    return 'unavailable';
  }
}
