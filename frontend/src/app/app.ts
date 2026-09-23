import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterOutlet, RouterLink } from '@angular/router';
import { IdentityService } from './features/identity/services/identity.service';
import { CartService } from './features/cart/cart.service';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly title = signal('De Lá do Pará');

  readonly identity = inject(IdentityService);
  readonly cartService = inject(CartService);
  private readonly platformId = inject(PLATFORM_ID);

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      void this.identity.fetchCurrent();
      void this.cartService.load();
    }
  }

  async logout(): Promise<void> {
    await this.identity.logout();
  }
}
