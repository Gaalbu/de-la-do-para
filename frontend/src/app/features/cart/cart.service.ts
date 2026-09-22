import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface CartItem {
  skuId: string;
  quantity: number;
}
export interface Cart {
  id: string;
  version: number;
  items: CartItem[];
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  readonly cart = signal<Cart | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      this.cart.set(await firstValueFrom(this.http.get<Cart>('/api/v1/cart')));
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  async replaceItems(items: CartItem[]): Promise<boolean> {
    const current = this.cart();
    if (!current) return false;
    try {
      this.cart.set(
        await firstValueFrom(
          this.http.put<Cart>('/api/v1/cart/items', {
            expectedVersion: current.version,
            items,
          }),
        ),
      );
      this.error.set(false);
      return true;
    } catch {
      await this.load();
      return false;
    }
  }

  async add(skuId: string): Promise<boolean> {
    const items = [...(this.cart()?.items ?? [])];
    const existing = items.find((item) => item.skuId === skuId);
    if (existing) existing.quantity += 1;
    else items.push({ skuId, quantity: 1 });
    return this.replaceItems(items);
  }

  async remove(skuId: string): Promise<boolean> {
    return this.replaceItems((this.cart()?.items ?? []).filter((item) => item.skuId !== skuId));
  }

  async clear(): Promise<boolean> {
    const current = this.cart();
    if (!current) return false;
    try {
      this.cart.set(
        await firstValueFrom(
          this.http.delete<Cart>('/api/v1/cart', {
            params: { expectedVersion: current.version },
          }),
        ),
      );
      return true;
    } catch {
      await this.load();
      return false;
    }
  }
}
