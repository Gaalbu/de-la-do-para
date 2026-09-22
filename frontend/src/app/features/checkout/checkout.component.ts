import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CheckoutService, PickupOption, ShippingQuote } from './checkout.service';

@Component({
  selector: 'app-checkout',
  imports: [FormsModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css',
})
export class CheckoutComponent {
  readonly checkout = inject(CheckoutService);
  postalCode = '';

  async quote(): Promise<void> {
    await this.checkout.quote(this.postalCode);
  }

  async select(option: ShippingQuote): Promise<void> {
    await this.checkout.select(option);
  }

  async selectPickup(option: PickupOption): Promise<void> {
    await this.checkout.selectPickup(option);
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }
}
