import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface CheckoutSnapshot {
  snapshotId: string;
  snapshotVersion: number;
}

export interface ShippingQuote {
  id: string;
  inputFingerprint: string;
  serviceName: string;
  priceCents: number;
  deliveryDays: number;
  preparationDays: number;
  packageSequences: number[];
  expiresAt: string;
}

export interface DeliveryOptions {
  snapshotId: string;
  snapshotVersion: number;
  inputFingerprint: string | null;
  options: ShippingQuote[];
}

export interface PickupOption {
  id: string;
  name: string;
  openingHours: string;
  preparationDays: number;
}

export interface PickupOptions {
  status: 'AVAILABLE' | 'UNAVAILABLE';
  options: PickupOption[];
  unavailableSkuIds: string[];
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly errorMessage = signal(
    'Não foi possível consultar este CEP. Confira os dados e tente novamente.',
  );
  readonly snapshot = signal<CheckoutSnapshot | null>(null);
  readonly options = signal<ShippingQuote[] | null>(null);
  readonly pickupOptions = signal<PickupOptions | null>(null);
  readonly selectedOption = signal<ShippingQuote | null>(null);

  async quote(postalCode: string): Promise<boolean> {
    this.loading.set(true);
    this.error.set(false);
    this.errorMessage.set(
      'Não foi possível consultar este CEP. Confira os dados e tente novamente.',
    );
    this.options.set(null);
    this.pickupOptions.set(null);
    this.selectedOption.set(null);
    try {
      const snapshot = await firstValueFrom(
        this.http.post<CheckoutSnapshot>('/api/v1/checkout/snapshots', {}),
      );
      const [response, pickup] = await Promise.all([
        firstValueFrom(
          this.http.get<DeliveryOptions>(
            `/api/v1/checkout/${snapshot.snapshotId}/delivery-options`,
            { params: { snapshotVersion: snapshot.snapshotVersion, postalCode } },
          ),
        ),
        firstValueFrom(
          this.http.get<PickupOptions>(`/api/v1/checkout/${snapshot.snapshotId}/pickup-options`, {
            params: { snapshotVersion: snapshot.snapshotVersion },
          }),
        ),
      ]);
      this.snapshot.set(snapshot);
      this.options.set(response.options);
      this.pickupOptions.set(pickup);
      return true;
    } catch {
      this.error.set(true);
      this.errorMessage.set(
        'Não foi possível consultar este CEP. Confira os dados e tente novamente.',
      );
      return false;
    } finally {
      this.loading.set(false);
    }
  }

  async select(option: ShippingQuote): Promise<boolean> {
    const snapshot = this.snapshot();
    if (!snapshot) return false;
    try {
      await firstValueFrom(
        this.http.post(
          `/api/v1/checkout/${snapshot.snapshotId}/delivery-selection`,
          {
            quoteId: option.id,
            inputFingerprint: option.inputFingerprint,
          },
          { params: { snapshotVersion: snapshot.snapshotVersion } },
        ),
      );
      this.selectedOption.set(option);
      return true;
    } catch {
      this.error.set(true);
      this.errorMessage.set('Esta cotação mudou ou expirou. Consulte as opções novamente.');
      return false;
    }
  }
}
