import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestRequest } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { CheckoutService } from './checkout.service';

describe('CheckoutService', () => {
  let service: CheckoutService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CheckoutService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CheckoutService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  it('creates a snapshot before requesting server-owned delivery options', async () => {
    const quote = service.quote('66053-000');
    const snapshot = http.expectOne('/api/v1/checkout/snapshots');
    expect(snapshot.request.method).toBe('POST');
    snapshot.flush({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    await Promise.resolve();

    const options = http.expectOne((request) =>
      request.urlWithParams.includes('/api/v1/checkout/snapshot-1/delivery-options'),
    );
    expect(options.request.params.get('snapshotVersion')).toBe('3');
    expect(options.request.params.get('postalCode')).toBe('66053-000');
    options.flush({
      snapshotId: 'snapshot-1',
      snapshotVersion: 3,
      options: [
        {
          id: 'quote-1',
          inputFingerprint: 'fingerprint-1',
          serviceName: 'Sandbox PAC',
          priceCents: 2590,
          deliveryDays: 5,
          preparationDays: 2,
          packageSequences: [1],
          expiresAt: '2026-09-22T13:00:00Z',
        },
      ],
    });
    const pickup = http.expectOne((request) =>
      request.urlWithParams.includes('/api/v1/checkout/snapshot-1/pickup-options'),
    );
    expect(pickup.request.params.get('snapshotVersion')).toBe('3');
    pickup.flush({ status: 'AVAILABLE', options: [], unavailableSkuIds: [] });

    expect(await quote).toBe(true);
    expect(service.options()?.[0].priceCents).toBe(2590);
    expect(service.pickupOptions()?.status).toBe('AVAILABLE');
  });

  it('loads the server-owned pickup origin for the same snapshot', async () => {
    const quote = service.quote('66053-000');
    http
      .expectOne('/api/v1/checkout/snapshots')
      .flush({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    await Promise.resolve();
    http
      .expectOne((request) => request.urlWithParams.includes('delivery-options'))
      .flush({
        snapshotId: 'snapshot-1',
        snapshotVersion: 3,
        inputFingerprint: null,
        options: [],
      });
    const pickup = http.expectOne((request) => request.urlWithParams.includes('pickup-options'));
    pickup.flush({
      status: 'AVAILABLE',
      options: [
        {
          id: 'PONTO-DEMO-BELEM',
          point: 'Ponto de demonstração — Belém',
          window: 'segunda a sexta',
          preparationDays: 1,
        },
      ],
      unavailableSkuIds: [],
    });

    expect(await quote).toBe(true);
    expect(service.pickupOptions()?.options[0].id).toBe('PONTO-DEMO-BELEM');
  });

  it('posts the selected server quote with the snapshot contract', async () => {
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    const option = {
      id: 'quote-1',
      inputFingerprint: 'fingerprint-1',
      serviceName: 'Sandbox PAC',
      priceCents: 2590,
      deliveryDays: 5,
      preparationDays: 2,
      packageSequences: [1],
      expiresAt: '2026-09-22T13:00:00Z',
    };
    const selection = service.select(option);
    const request = http.expectOne((candidate) =>
      candidate.urlWithParams.includes('delivery-selection'),
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('snapshotVersion')).toBe('3');
    expect(request.request.body).toEqual({ quoteId: 'quote-1', inputFingerprint: 'fingerprint-1' });
    request.flush({ quoteId: 'quote-1', snapshotVersion: 3, inputFingerprint: 'fingerprint-1' });

    expect(await selection).toBe(true);
    expect(service.selectedOption()).toEqual(option);
  });

  it('exposes a recoverable message when server selection is rejected', async () => {
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    const option = {
      id: 'quote-1',
      inputFingerprint: 'fingerprint-1',
      serviceName: 'Sandbox PAC',
      priceCents: 2590,
      deliveryDays: 5,
      preparationDays: 2,
      packageSequences: [1],
      expiresAt: '2026-09-22T13:00:00Z',
    };
    const selection = service.select(option);
    const request = http.expectOne((candidate) =>
      candidate.urlWithParams.includes('delivery-selection'),
    );
    request.flush({ codigo: 'CHECKOUT_005' }, { status: 410, statusText: 'Gone' });

    expect(await selection).toBe(false);
    expect(service.errorMessage()).toContain('mudou ou expirou');
    expect(service.selectedOption()).toBeNull();
  });

  it('posts the selected pickup option with the snapshot contract', async () => {
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    const option = {
      id: 'PONTO-DEMO-BELEM',
      point: 'Ponto de demonstração — Belém',
      window: 'segunda a sexta',
      preparationDays: 1,
    };
    const selection = service.selectPickup(option);
    const request = http.expectOne((candidate) =>
      candidate.urlWithParams.includes('pickup-selection'),
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('snapshotVersion')).toBe('3');
    expect(request.request.body).toEqual({ pickupOptionId: 'PONTO-DEMO-BELEM' });
    request.flush(option);

    expect(await selection).toBe(true);
    expect(service.selectedPickupOption()).toEqual(option);
    expect(service.selectedOption()).toBeNull();
  });

  it('loads the server price summary for the chosen pickup before purchase', async () => {
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    const selection = service.loadSummary({ mode: 'PICKUP', pickupOptionId: 'PONTO-DEMO-BELEM' });
    const request = http.expectOne((candidate) => candidate.urlWithParams.includes('/summary'));
    expect(request.request.params.get('mode')).toBe('PICKUP');
    expect(request.request.params.get('pickupOptionId')).toBe('PONTO-DEMO-BELEM');
    request.flush({
      snapshotId: 'snapshot-1',
      snapshotVersion: 3,
      lines: [],
      fulfillment: {
        mode: 'PICKUP',
        optionId: 'PONTO-DEMO-BELEM',
        label: 'Ponto de demonstração — Belém',
        shippingCents: 0,
        preparationDays: 1,
        deliveryDays: null,
      },
      couponCode: null,
      subtotalCents: 2000,
      shippingCents: 0,
      discountCents: 0,
      totalCents: 2000,
      summaryVersion: 'summary-hash',
    });

    expect(await selection).toBe(true);
    expect(service.summary()?.totalCents).toBe(2000);
  });

  it('keeps the idempotency key when an acceptance request can be retried', async () => {
    window.sessionStorage.clear();
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    service.summary.set({
      snapshotId: 'snapshot-1',
      snapshotVersion: 3,
      lines: [],
      fulfillment: {
        mode: 'PICKUP',
        optionId: 'PONTO-DEMO-BELEM',
        label: 'Ponto de demonstração — Belém',
        shippingCents: 0,
        preparationDays: 1,
        deliveryDays: null,
      },
      couponCode: null,
      subtotalCents: 2000,
      shippingCents: 0,
      discountCents: 0,
      totalCents: 2000,
      summaryVersion: 'summary-hash',
    });
    const intent = {
      mode: 'PICKUP' as const,
      pickupOptionId: 'PONTO-DEMO-BELEM',
      email: 'ana@example.com',
      address: {
        recipientName: 'Ana Teste',
        street: 'Rua das Mangueiras',
        number: '123',
        district: 'Centro',
        city: 'Belém',
        state: 'PA',
      },
    };

    const first = service.acceptPurchase(intent);
    let firstRequest!: TestRequest;
    await vi.waitFor(() => {
      firstRequest = http.expectOne('/api/v1/checkout/snapshot-1/purchase');
    });
    const key = firstRequest.request.headers.get('Idempotency-Key');
    expect(key?.length).toBeGreaterThanOrEqual(16);
    firstRequest.flush({ codigo: 'CHECKOUT_010' }, { status: 409, statusText: 'Conflict' });
    expect(await first).toBeNull();

    const retry = service.acceptPurchase(intent);
    let retryRequest!: TestRequest;
    await vi.waitFor(() => {
      retryRequest = http.expectOne('/api/v1/checkout/snapshot-1/purchase');
    });
    expect(retryRequest.request.headers.get('Idempotency-Key')).toBe(key);
    retryRequest.flush({
      orderId: 'order-1',
      status: 'PENDING_PAYMENT',
      totalCents: 2000,
      reservationExpiresAt: null,
      accessToken: 'guest-order-token',
      replayed: false,
    });

    expect((await retry)?.orderId).toBe('order-1');
    expect(window.sessionStorage.getItem('dlp.order-token.order-1')).toBe('guest-order-token');
    const storageKeys = Object.keys(window.sessionStorage);
    expect(storageKeys.some((storageKey) => storageKey.startsWith('dlp.purchase-key.'))).toBe(true);
    expect(storageKeys.join(' ')).not.toContain('ana@example.com');
    expect(storageKeys.join(' ')).not.toContain('Rua das Mangueiras');
    expect(storageKeys.join(' ')).not.toContain('Ana Teste');
    expect(storageKeys.join(' ')).not.toContain('PONTO-DEMO-BELEM');
  });

  it('releases the accepting state when secure retry storage is unavailable', async () => {
    vi.stubGlobal('crypto', { subtle: undefined });
    service.snapshot.set({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    service.summary.set({
      snapshotId: 'snapshot-1',
      snapshotVersion: 3,
      lines: [],
      fulfillment: {
        mode: 'PICKUP',
        optionId: 'PONTO-DEMO-BELEM',
        label: 'Ponto de demonstração — Belém',
        shippingCents: 0,
        preparationDays: 1,
        deliveryDays: null,
      },
      couponCode: null,
      subtotalCents: 2000,
      shippingCents: 0,
      discountCents: 0,
      totalCents: 2000,
      summaryVersion: 'summary-hash',
    });

    expect(
      await service.acceptPurchase({ mode: 'PICKUP', pickupOptionId: 'PONTO-DEMO-BELEM' }),
    ).toBe(null);
    expect(service.accepting()).toBe(false);
    expect(service.purchaseError()).toContain('tente novamente');
    http.expectNone('/api/v1/checkout/snapshot-1/purchase');
  });
});
