import { HttpClient } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface CartItem {
  skuId: string;
  quantity: number;
}
interface Cart {
  id: string;
  version: number;
  items: CartItem[];
}
interface MergeView {
  accountCart: Cart;
  guestCart: Cart;
  inactiveSkuIds: string[];
}
type MergeChoice = 'KEEP_ACCOUNT' | 'REPLACE_WITH_GUEST' | 'COMBINE';

@Component({
  selector: 'app-cart-merge',
  imports: [RouterLink, NgTemplateOutlet],
  template: `
    <main class="merge" aria-labelledby="merge-title">
      <h1 id="merge-title">Escolha como organizar seus carrinhos</h1>
      @if (loading()) {
        <p role="status">Carregando seus carrinhos…</p>
      } @else if (error()) {
        <p role="alert" class="error">{{ error() }}</p>
        <a routerLink="/cart">Voltar ao carrinho</a>
      } @else if (view()) {
        <p>
          Você pode manter o carrinho da conta, substituí-lo pelo carrinho desta visita ou combinar
          as quantidades por item.
        </p>
        <div class="carts">
          <section aria-labelledby="account-title">
            <h2 id="account-title">Carrinho da conta</h2>
            <ng-container
              [ngTemplateOutlet]="items"
              [ngTemplateOutletContext]="{
                $implicit: view()!.accountCart,
                inactiveSkuIds: view()!.inactiveSkuIds,
              }"
            />
          </section>
          <section aria-labelledby="guest-title">
            <h2 id="guest-title">Carrinho desta visita</h2>
            <ng-container
              [ngTemplateOutlet]="items"
              [ngTemplateOutletContext]="{
                $implicit: view()!.guestCart,
                inactiveSkuIds: view()!.inactiveSkuIds,
              }"
            />
          </section>
        </div>
        @if (error()) {
          <p role="alert" class="error">{{ error() }}</p>
        }
        <div class="choices" aria-label="Escolha o que fazer">
          <button type="button" [disabled]="saving()" (click)="resolve('KEEP_ACCOUNT')">
            Manter carrinho da conta
          </button>
          <button type="button" [disabled]="saving()" (click)="resolve('REPLACE_WITH_GUEST')">
            Substituir pelo carrinho desta visita
          </button>
          <button type="button" [disabled]="saving()" (click)="resolve('COMBINE')">
            Combinar quantidades por item
          </button>
        </div>
      }
      <ng-template #items let-cart let-inactiveSkuIds="inactiveSkuIds">
        @if (cart.items.length === 0) {
          <p>Sem itens.</p>
        }
        <ul>
          @for (item of cart.items; track item.skuId) {
            <li>
              Item {{ item.skuId }} — quantidade {{ item.quantity }}
              @if (inactiveSkuIds.includes(item.skuId)) {
                <span class="inactive">Indisponível no catálogo; será revalidado no checkout.</span>
              }
            </li>
          }
        </ul>
      </ng-template>
    </main>
  `,
  styles: `
    .merge {
      max-width: 62rem;
      margin: 2rem auto;
      padding: 1rem;
    }
    .carts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr));
      gap: 1rem;
    }
    section {
      border: 1px solid #b8c6bd;
      border-radius: 0.5rem;
      padding: 1rem;
    }
    .choices {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin-top: 1.5rem;
    }
    button {
      padding: 0.75rem 1rem;
    }
    .error {
      color: #b91c1c;
    }
    .inactive {
      display: block;
      color: #8b4513;
      font-weight: 600;
    }
  `,
})
export class CartMergeComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly view = signal<MergeView | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.view.set(await firstValueFrom(this.http.get<MergeView>('/api/v1/cart/merge')));
    } catch {
      this.error.set(
        'Não foi possível carregar a escolha de carrinho. Entre novamente para continuar.',
      );
    } finally {
      this.loading.set(false);
    }
  }

  async resolve(choice: MergeChoice): Promise<void> {
    const current = this.view();
    if (!current || this.saving()) return;
    this.error.set(null);
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.get('/api/v1/csrf'));
      await firstValueFrom(
        this.http.post<Cart>('/api/v1/cart/merge', {
          choice,
          expectedAccountVersion: current.accountCart.version,
          expectedGuestVersion: current.guestCart.version,
        }),
      );
      await this.router.navigateByUrl('/cart');
    } catch {
      this.error.set(
        'Os carrinhos mudaram ou a sessão expirou. Confira novamente antes de escolher.',
      );
      await this.load();
    } finally {
      this.saving.set(false);
    }
  }
}
