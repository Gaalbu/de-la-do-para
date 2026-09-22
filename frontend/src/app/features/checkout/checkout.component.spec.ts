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
            serviceName: 'Sandbox PAC',
            priceCents: 2590,
            deliveryDays: 5,
            preparationDays: 2,
            expiresAt: '2026-09-22T13:00:00Z',
          },
        ],
      });
    await quote;
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Sandbox PAC');
    expect(text).toMatch(/R\$\s*25,90/);
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
