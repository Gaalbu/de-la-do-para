import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  CheckoutService,
  PickupOption,
  PurchaseSelection,
  ShippingQuote,
} from './checkout.service';

@Component({
  selector: 'app-checkout',
  imports: [FormsModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css',
})
export class CheckoutComponent {
  readonly checkout = inject(CheckoutService);
  private readonly router = inject(Router);
  postalCode = '';
  email = '';
  couponCode = '';
  recipientName = '';
  street = '';
  number = '';
  complement = '';
  district = '';
  city = '';
  state = '';
  readonly summaryLoading = signal(false);
  readonly purchaseSelection = signal<PurchaseSelection | null>(null);

  async quote(): Promise<void> {
    this.purchaseSelection.set(null);
    this.checkout.summary.set(null);
    await this.checkout.quote(this.postalCode);
  }

  async select(option: ShippingQuote): Promise<void> {
    await this.checkout.select(option);
  }

  async selectPickup(option: PickupOption): Promise<void> {
    await this.checkout.selectPickup(option);
  }

  async reviewDelivery(option: ShippingQuote): Promise<void> {
    this.purchaseSelection.set(null);
    this.checkout.summary.set(null);
    if (!(await this.checkout.select(option))) return;
    await this.review({
      mode: 'DELIVERY',
      quoteId: option.id,
      inputFingerprint: option.inputFingerprint,
    });
  }

  async reviewPickup(option: PickupOption): Promise<void> {
    this.purchaseSelection.set(null);
    this.checkout.summary.set(null);
    if (!(await this.checkout.selectPickup(option))) return;
    await this.review({ mode: 'PICKUP', pickupOptionId: option.id });
  }

  private async review(selection: PurchaseSelection): Promise<void> {
    this.purchaseSelection.set(selection);
    this.summaryLoading.set(true);
    await this.checkout.loadSummary(this.selection());
    this.summaryLoading.set(false);
  }

  selection(): PurchaseSelection {
    const selected = this.purchaseSelection();
    if (!selected) return { mode: 'PICKUP' };
    return {
      ...selected,
      couponCode: this.couponCode.trim().toUpperCase() || undefined,
      email: this.email.trim().toLowerCase() || undefined,
      address:
        selected.mode === 'DELIVERY'
          ? {
              recipientName: this.recipientName.trim(),
              street: this.street.trim(),
              number: this.number.trim(),
              complement: this.complement.trim() || undefined,
              district: this.district.trim(),
              city: this.city.trim(),
              state: this.state.trim().toUpperCase(),
            }
          : undefined,
    };
  }

  async refreshSummary(): Promise<void> {
    if (!this.purchaseSelection()) return;
    this.summaryLoading.set(true);
    await this.checkout.loadSummary(this.selection());
    this.summaryLoading.set(false);
  }

  async accept(): Promise<void> {
    if (!this.canAccept()) return;
    const accepted = await this.checkout.acceptPurchase(this.selection());
    if (accepted) await this.router.navigate(['/orders', accepted.orderId]);
  }

  canAccept(): boolean {
    const selection = this.purchaseSelection();
    const summary = this.checkout.summary();
    if (
      !selection ||
      !summary ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim()) ||
      this.summaryLoading()
    ) {
      return false;
    }
    if ((summary.couponCode ?? '') !== this.couponCode.trim().toUpperCase()) return false;
    if (selection.mode === 'DELIVERY') {
      return Boolean(
        this.recipientName.trim() &&
        this.street.trim() &&
        this.number.trim() &&
        this.district.trim() &&
        this.city.trim() &&
        this.state.trim().length === 2,
      );
    }
    return true;
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }
}
