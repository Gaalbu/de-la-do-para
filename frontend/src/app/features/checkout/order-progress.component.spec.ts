import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OrderProgressComponent } from './order-progress.component';

describe('OrderProgressComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderProgressComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'order-1' }) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows partial delivery independently by package group', async () => {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/order-1').flush(order('DELIVERY', 'PAID'));
    http.expectOne('/api/v1/orders/order-1/shipping-progress').flush({
      shipments: [
        { packageSequences: [1, 2], status: 'DELIVERED', exception: false, updatedAt: null },
        { packageSequences: [3], status: 'IN_TRANSIT', exception: false, updatedAt: null },
      ],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pacotes 1 e 2');
    expect(text).toContain('Pacote entregue');
    expect(text).toContain('Pacote 3');
    expect(text).toContain('Envio em trânsito');
    expect(text).not.toContain('Pedido entregue');
  });

  it('does not repeat an aggregate delivered status when a package is still moving', async () => {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/order-1').flush(order('DELIVERY', 'DELIVERED'));
    http.expectOne('/api/v1/orders/order-1/shipping-progress').flush({
      shipments: [
        { packageSequences: [1], status: 'DELIVERED', exception: false, updatedAt: null },
        { packageSequences: [2], status: 'IN_TRANSIT', exception: false, updatedAt: null },
      ],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Status geral em atualização');
    expect(text).not.toContain('Pedido entregue');
    expect(text).toContain('Envio em trânsito');
  });

  it('shows an authorized pickup code only after pickup is ready', async () => {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/order-1').flush(order('PICKUP', 'READY_FOR_PICKUP'));
    const pickup = http.expectOne('/api/v1/orders/order-1/pickup');
    expect(pickup.request.withCredentials).toBe(false);
    pickup.flush({
      code: 'ABCD123456',
      point: 'Ponto de retirada',
      window: 'Segunda a sexta',
      retentionBusinessDays: 3,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ABCD123456');
    expect(text).toContain('Ponto de retirada');
    expect(http.match('/api/v1/orders/order-1/shipping-progress')).toHaveLength(0);
  });

  it('keeps an unavailable tracking read separate from the order status', async () => {
    const fixture = TestBed.createComponent(OrderProgressComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/order-1').flush(order('DELIVERY', 'PAID'));
    http.expectOne('/api/v1/orders/order-1/shipping-progress').flush(
      { title: 'Unavailable' },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pagamento confirmado');
    expect(text).toContain('Não foi possível atualizar o andamento da entrega.');
  });
});

function order(mode: 'DELIVERY' | 'PICKUP', status: string) {
  return {
    id: 'order-1',
    mode,
    status,
    totalCents: 1000,
    items: [{ productName: 'Produto', quantity: 1, lineTotalCents: 1000 }],
    payment: null,
  };
}
