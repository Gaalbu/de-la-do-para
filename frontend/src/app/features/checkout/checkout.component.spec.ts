import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CheckoutComponent } from './checkout.component';

describe('CheckoutComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the quote action disabled until a postal code is provided', () => {
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('button[type="submit"]'),
    ).toHaveProperty('disabled', true);
  });

  it('renders only server-returned delivery options', async () => {
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.postalCode = '66053-000';
    const quote = component.quote();
    http.expectOne('/api/v1/checkout/snapshots').flush({
      snapshotId: 'snapshot-1',
      snapshotVersion: 3,
    });
    await Promise.resolve();
    http
      .expectOne((request) => request.urlWithParams.includes('delivery-options'))
      .flush({
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
    http
      .expectOne((request) => request.urlWithParams.includes('pickup-options'))
      .flush({ status: 'UNAVAILABLE', options: [], unavailableSkuIds: [] });
    await quote;
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Sandbox PAC');
    expect(text).toContain('Pacotes: 1');
    expect(text).toMatch(/R\$\s*25,90/);
  });

  it('selects a displayed delivery option through the server', async () => {
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.postalCode = '66053-000';
    const quote = component.quote();
    http
      .expectOne('/api/v1/checkout/snapshots')
      .flush({ snapshotId: 'snapshot-1', snapshotVersion: 3 });
    await Promise.resolve();
    http
      .expectOne((request) => request.urlWithParams.includes('delivery-options'))
      .flush({
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
    http
      .expectOne((request) => request.urlWithParams.includes('pickup-options'))
      .flush({ status: 'UNAVAILABLE', options: [], unavailableSkuIds: [] });
    await quote;
    fixture.detectChanges();

    const selection = component.select(component.checkout.options()![0]);
    const request = http.expectOne((candidate) =>
      candidate.urlWithParams.includes('delivery-selection'),
    );
    request.flush({ quoteId: 'quote-1', snapshotVersion: 3, inputFingerprint: 'fingerprint-1' });
    await selection;
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Selecionada');
  });

  it('shows a recoverable error when the snapshot cannot be created', async () => {
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.postalCode = '66053-000';
    const quote = component.quote();
    http
      .expectOne('/api/v1/checkout/snapshots')
      .flush({ title: 'Erro', codigo: 'CHECKOUT_001' }, { status: 404, statusText: 'Not Found' });
    await quote;
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
  });
});
