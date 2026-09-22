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
          serviceName: 'Sandbox PAC',
          priceCents: 2590,
          deliveryDays: 5,
          preparationDays: 2,
          expiresAt: '2026-09-22T13:00:00Z',
        },
      ],
    });

    expect(await quote).toBe(true);
    expect(service.options()?.[0].priceCents).toBe(2590);
  });
});
