import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ProductDetailComponent } from './product-detail.component';

describe('ProductDetailComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ slug: 'abs-demo' })) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders provenance, current price, and an unavailable SKU state', async () => {
    const fixture = TestBed.createComponent(ProductDetailComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/v1/products/abs-demo');
    request.flush({
      slug: 'abs-demo',
      displayName: 'Produto de demonstração',
      description: 'Descrição editorial.',
      category: 'FOOD',
      image: null,
      producer: {
        displayName: 'Produtor de demonstração',
        originLabel: 'Belém (demonstração)',
        description: 'Origem editorial.',
      },
      skus: [
        {
          skuCode: 'SKU-100',
          salesUnit: 'pacote',
          netContentGrams: 100,
          minimumShelfLifeDays: 30,
          priceCents: 1800,
          availableUnits: 0,
        },
      ],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Belém');
    expect(text).toMatch(/R\$\s*18,00/);
    expect(text).toContain('Indisponível no momento');
  });
});
