import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
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
  point: string;
  window: string;
  preparationDays: number;
}

export interface PickupOptions {
  status: 'AVAILABLE' | 'UNAVAILABLE';
  options: PickupOption[];
  unavailableSkuIds: string[];
}

export interface PurchaseSelection {
  mode: 'DELIVERY' | 'PICKUP';
  quoteId?: string;
  inputFingerprint?: string;
  pickupOptionId?: string;
  couponCode?: string;
  email?: string;
  address?: {
    recipientName: string;
    street: string;
    number: string;
    complement?: string;
    district: string;
    city: string;
    state: string;
  };
}

export interface PurchaseSummary {
  snapshotId: string;
  snapshotVersion: number;
  lines: Array<{
    skuId: string;
    productName: string;
    salesUnit: string;
    quantity: number;
    unitPriceCents: number;
    lineTotalCents: number;
  }>;
  fulfillment: {
    mode: 'DELIVERY' | 'PICKUP';
    optionId: string;
    label: string;
    detail?: string;
    shippingCents: number;
    preparationDays: number;
    deliveryDays: number | null;
  };
  couponCode: string | null;
  subtotalCents: number;
  shippingCents: number;
  discountCents: number;
  totalCents: number;
  summaryVersion: string;
}

export interface AcceptedPurchase {
  orderId: string;
  status: string;
  totalCents: number;
  reservationExpiresAt: string | null;
  accessToken: string | null;
  replayed: boolean;
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly errorMessage = signal(
    'Não foi possível consultar este CEP. Confira os dados e tente novamente.',
  );
  readonly snapshot = signal<CheckoutSnapshot | null>(null);
  readonly options = signal<ShippingQuote[] | null>(null);
  readonly pickupOptions = signal<PickupOptions | null>(null);
  readonly selectedOption = signal<ShippingQuote | null>(null);
  readonly selectedPickupOption = signal<PickupOption | null>(null);
  readonly summary = signal<PurchaseSummary | null>(null);
  readonly purchaseError = signal('');
  readonly accepting = signal(false);

  async quote(postalCode: string): Promise<boolean> {
    this.loading.set(true);
    this.error.set(false);
    this.errorMessage.set(
      'Não foi possível consultar este CEP. Confira os dados e tente novamente.',
    );
    this.options.set(null);
    this.pickupOptions.set(null);
    this.selectedOption.set(null);
    this.selectedPickupOption.set(null);
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
      this.selectedPickupOption.set(null);
      return true;
    } catch {
      this.error.set(true);
      this.errorMessage.set('Esta cotação mudou ou expirou. Consulte as opções novamente.');
      return false;
    }
  }

  async selectPickup(option: PickupOption): Promise<boolean> {
    const snapshot = this.snapshot();
    if (!snapshot) return false;
    try {
      await firstValueFrom(
        this.http.post(
          `/api/v1/checkout/${snapshot.snapshotId}/pickup-selection`,
          { pickupOptionId: option.id },
          { params: { snapshotVersion: snapshot.snapshotVersion } },
        ),
      );
      this.selectedPickupOption.set(option);
      this.selectedOption.set(null);
      return true;
    } catch {
      this.error.set(true);
      this.errorMessage.set('Esta opção de retirada mudou. Consulte as opções novamente.');
      return false;
    }
  }

  async loadSummary(selection: PurchaseSelection): Promise<boolean> {
    const snapshot = this.snapshot();
    if (!snapshot) return false;
    this.purchaseError.set('');
    try {
      const params: Record<string, string | number> = {
        snapshotVersion: snapshot.snapshotVersion,
        mode: selection.mode,
      };
      if (selection.mode === 'DELIVERY') {
        if (!selection.quoteId || !selection.inputFingerprint) return false;
        params['quoteId'] = selection.quoteId;
        params['inputFingerprint'] = selection.inputFingerprint;
      } else {
        if (!selection.pickupOptionId) return false;
        params['pickupOptionId'] = selection.pickupOptionId;
      }
      if (selection.couponCode?.trim()) params['couponCode'] = selection.couponCode.trim();
      this.summary.set(
        await firstValueFrom(
          this.http.get<PurchaseSummary>(`/api/v1/checkout/${snapshot.snapshotId}/summary`, {
            params,
          }),
        ),
      );
      return true;
    } catch {
      this.purchaseError.set(
        'Não foi possível calcular o resumo. Confira a opção e tente novamente.',
      );
      this.summary.set(null);
      return false;
    }
  }

  async acceptPurchase(selection: PurchaseSelection): Promise<AcceptedPurchase | null> {
    const snapshot = this.snapshot();
    const summary = this.summary();
    if (!snapshot || !summary || this.accepting()) return null;
    this.accepting.set(true);
    this.purchaseError.set('');
    const intent = JSON.stringify({
      snapshotId: snapshot.snapshotId,
      selection,
      summaryVersion: summary.summaryVersion,
    });
    const storage = this.document.defaultView?.sessionStorage;
    const storageKey = `dlp.purchase-key.${encodeURIComponent(intent)}`;
    let idempotencyKey = storage?.getItem(storageKey) ?? null;
    if (!idempotencyKey) {
      idempotencyKey = this.document.defaultView?.crypto.randomUUID() ?? this.uuidFallback();
      storage?.setItem(storageKey, idempotencyKey);
    }
    const body: Record<string, unknown> = {
      snapshotVersion: snapshot.snapshotVersion,
      mode: selection.mode,
      summaryVersion: summary.summaryVersion,
    };
    if (selection.quoteId) body['quoteId'] = selection.quoteId;
    if (selection.inputFingerprint) body['inputFingerprint'] = selection.inputFingerprint;
    if (selection.pickupOptionId) body['pickupOptionId'] = selection.pickupOptionId;
    if (selection.couponCode?.trim()) body['couponCode'] = selection.couponCode.trim();
    if (selection.email?.trim()) body['email'] = selection.email.trim();
    if (selection.address) body['address'] = selection.address;
    try {
      const accepted = await firstValueFrom(
        this.http.post<AcceptedPurchase>(`/api/v1/checkout/${snapshot.snapshotId}/purchase`, body, {
          headers: { 'Idempotency-Key': idempotencyKey },
        }),
      );
      storage?.setItem(`dlp.order-token.${accepted.orderId}`, accepted.accessToken ?? '');
      return accepted;
    } catch (error) {
      const code = (error as { error?: { codigo?: string } }).error?.codigo;
      this.purchaseError.set(
        code === 'CHECKOUT_012'
          ? 'O resumo mudou. Revise os valores atualizados antes de aceitar a compra.'
          : 'Não foi possível aceitar a compra. Seus dados foram mantidos; tente novamente.',
      );
      return null;
    } finally {
      this.accepting.set(false);
    }
  }

  private uuidFallback(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
      const random = Math.floor(Math.random() * 16);
      return (character === 'x' ? random : (random & 3) | 8).toString(16);
    });
  }
}
