import { Component, OnInit, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { IdentityService } from './features/identity/services/identity.service';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly title = signal('De Lá do Pará');

  readonly identity = inject(IdentityService);
  private readonly platformId = inject(PLATFORM_ID);

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      void this.identity.fetchCurrent();
    }
  }

  async logout(): Promise<void> {
    await this.identity.logout();
  }
}
