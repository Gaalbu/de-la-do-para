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

    expect(await quote).toBe(true);
    expect(service.options()?.[0].priceCents).toBe(2590);
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
});
