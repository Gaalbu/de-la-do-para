import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from './cart.service';

@Component({
  imports: [RouterLink],
  selector: 'app-cart',
  styleUrl: './cart.component.css',
  templateUrl: './cart.component.html',
})
export class CartComponent implements OnInit {
  readonly cartService = inject(CartService);
  ngOnInit(): void {
    void this.cartService.load();
  }
  async remove(skuId: string): Promise<void> {
    await this.cartService.remove(skuId);
  }
  async increment(skuId: string): Promise<void> {
    await this.cartService.replaceItems(
      (this.cartService.cart()?.items ?? []).map((item) =>
        item.skuId === skuId ? { ...item, quantity: item.quantity + 1 } : item,
      ),
    );
  }
  async decrement(skuId: string): Promise<void> {
    await this.cartService.replaceItems(
      (this.cartService.cart()?.items ?? [])
        .map((item) => (item.skuId === skuId ? { ...item, quantity: item.quantity - 1 } : item))
        .filter((item) => item.quantity > 0),
    );
  }
  async clear(): Promise<void> {
    await this.cartService.clear();
  }
}
