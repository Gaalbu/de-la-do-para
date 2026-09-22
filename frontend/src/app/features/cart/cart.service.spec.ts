import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { CartService } from './cart.service';

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CartService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a persistent cart and sends the version read by the client', async () => {
    const loading = service.load();
    http
      .expectOne('/api/v1/cart')
      .flush({ id: 'cart-1', version: 3, items: [{ skuId: 'sku-1', quantity: 2 }] });
    await loading;

    const update = service.remove('sku-1');
    const request = http.expectOne('/api/v1/cart/items');
    expect(request.request.body).toEqual({ expectedVersion: 3, items: [] });
    request.flush({ id: 'cart-1', version: 4, items: [] });
    await update;
    expect(service.cart()?.version).toBe(4);
  });
});
