import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
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

  afterEach(() => http.verify());

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
});
