import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin',
  imports: [RouterLink],
  template: `
    <section aria-labelledby="admin-title">
      <h1 id="admin-title">Administração</h1>
      <p>Bem-vindo ao painel administrativo.</p>
      <nav aria-label="Administração">
        <a routerLink="/">Vitrine</a>
      </nav>
    </section>
  `,
})
export class AdminComponent {}
