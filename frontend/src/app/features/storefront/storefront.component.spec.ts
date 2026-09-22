import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StorefrontComponent } from './storefront.component';

const page = {
  content: [
    {
      slug: 'chocolate-demo',
      displayName: 'Chocolate de demonstração',
      category: 'FOOD' as const,
      producer: { displayName: 'Coletivo Cacau', originLabel: 'Sul do Pará (demonstração)' },
      skus: [{ skuCode: 'CHOC-100', salesUnit: 'barra', priceCents: 2500, availableUnits: 2 }],
    },
  ],
  page: 0,
  size: 12,
  totalElements: 1,
  totalPages: 1,
};

describe('StorefrontComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StorefrontComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the public catalog and renders price and provenance', async () => {
    const fixture = TestBed.createComponent(StorefrontComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/products?sort=RELEVANCE&page=0&size=12').flush(page);
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Chocolate de demonstração');
    expect(text).toContain('Sul do Pará');
    expect(text).toMatch(/R\$\s*25,00/);
  });

  it('shows an empty state when filters return no products', async () => {
    const fixture = TestBed.createComponent(StorefrontComponent);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/products?sort=RELEVANCE&page=0&size=12')
      .flush({ ...page, content: [], totalElements: 0, totalPages: 0 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Nenhum produto encontrado',
    );
  });

  it('shows a retry action when the public query fails', async () => {
    const fixture = TestBed.createComponent(StorefrontComponent);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/products?sort=RELEVANCE&page=0&size=12')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Não foi possível carregar',
    );
  });
});
