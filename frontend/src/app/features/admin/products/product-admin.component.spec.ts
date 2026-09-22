import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProductAdminComponent } from './product-admin.component';

const producer = {
  id: '2c50c447-2c60-4bf6-9207-4028685019c0',
  slug: 'cacau-teste',
  displayName: 'Coletivo Cacau de Demonstração',
  originLabel: 'Sul do Pará (demonstração)',
  description: 'Texto editorial fictício para demonstração.',
  demonstration: true as const,
  active: true,
  createdAt: '2026-09-21T12:00:00Z',
  updatedAt: '2026-09-21T12:00:00Z',
};

const product = {
  id: '77511f63-dfe6-4ac0-86ca-d00a858293f7',
  slug: 'chocolate-demo',
  displayName: 'Chocolate de demonstração',
  description: 'Descrição editorial fictícia.',
  category: 'FOOD' as const,
  producerId: producer.id,
  demonstration: true as const,
  active: true,
  skus: [
    {
      id: '2a511f63-dfe6-4ac0-86ca-d00a858293f7',
      skuCode: 'CHOC-DEMO',
      salesUnit: 'barra',
      netContentGrams: 100,
      minimumShelfLifeDays: 30,
      fragile: false,
      lengthMm: 120,
      widthMm: 80,
      heightMm: 20,
      grossWeightGrams: 120,
      active: true,
      createdAt: '2026-09-21T12:00:00Z',
      updatedAt: '2026-09-21T12:00:00Z',
    },
  ],
  createdAt: '2026-09-21T12:00:00Z',
  updatedAt: '2026-09-21T12:00:00Z',
};

describe('ProductAdminComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductAdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createLoadedFixture() {
    const fixture = TestBed.createComponent(ProductAdminComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/producers?page=0&size=50').flush({
      content: [producer],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne('/api/v1/admin/products?page=0&size=20').flush({
      content: [product],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    return fixture;
  }

  it('loads products with their category, producer and demonstration status', async () => {
    const fixture = createLoadedFixture();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain(product.displayName);
    expect(text).toContain('Alimento');
    expect(text).toContain(producer.displayName);
    expect(text).toContain('Demonstração');
  });

  it('lets the administrator retry loading producers after a failure', async () => {
    const fixture = TestBed.createComponent(ProductAdminComponent);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/admin/producers?page=0&size=50')
      .flush({ codigo: 'UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/v1/admin/products?page=0&size=20').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const retryButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((button) => button.textContent?.includes('Tentar carregar produtores novamente'));
    expect(retryButton).toBeDefined();
    retryButton?.click();
    http.expectOne('/api/v1/admin/producers?page=0&size=50').flush({
      content: [producer],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();

    expect(fixture.componentInstance.producersError()).toBeNull();
    expect(fixture.componentInstance.selectableProducers()).toHaveLength(1);
  });

  it('creates a food product, surfaces a conflict, and retries with corrected data', async () => {
    const fixture = createLoadedFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.form = {
      slug: product.slug,
      displayName: product.displayName,
      description: product.description,
      category: 'FOOD',
      producerId: producer.id,
      active: true,
      skus: [
        {
          skuCode: 'CHOC-DEMO-NEW',
          salesUnit: 'barra',
          netContentGrams: 100,
          minimumShelfLifeDays: 30,
          fragile: false,
          lengthMm: 120,
          widthMm: 80,
          heightMm: 20,
          grossWeightGrams: 120,
          active: true,
        },
      ],
    };

    const failedSave = component.save();
    const rejected = http.expectOne('/api/v1/admin/products');
    expect(rejected.request.method).toBe('POST');
    expect(rejected.request.body.skus[0].minimumShelfLifeDays).toBe(30);
    rejected.flush(
      { codigo: 'CATALOG_CONFLICT', detail: 'Duplicado' },
      { status: 409, statusText: 'Conflict' },
    );
    await failedSave;
    expect(component.saveError()).toContain('Não foi possível salvar');

    component.form.slug = 'chocolate-demo-corrigido';
    const retry = component.save();
    const created = http.expectOne('/api/v1/admin/products');
    expect(created.request.body.slug).toBe('chocolate-demo-corrigido');
    created.flush(product);
    await Promise.resolve();
    http.expectOne('/api/v1/admin/products?page=0&size=20').flush({
      content: [product],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    await retry;
    expect(component.saveError()).toBeNull();
  });

  it('updates a craft product without food-only fields', async () => {
    const craftProduct = {
      ...product,
      slug: 'cuia-demo',
      displayName: 'Cuia de demonstração',
      category: 'CRAFT' as const,
      skus: [
        {
          ...product.skus[0],
          skuCode: 'CUIA-DEMO',
          netContentGrams: null,
          minimumShelfLifeDays: null,
          fragile: true,
        },
      ],
    };
    const fixture = createLoadedFixture();
    await fixture.whenStable();
    fixture.componentInstance.startEdit(craftProduct);
    fixture.componentInstance.form.displayName = 'Cuia atualizada de demonstração';

    const saving = fixture.componentInstance.save();
    const update = http.expectOne(`/api/v1/admin/products/${product.id}`);
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body.category).toBe('CRAFT');
    expect(update.request.body.skus[0].netContentGrams).toBeNull();
    expect(update.request.body.skus[0].minimumShelfLifeDays).toBeNull();
    expect(update.request.body.skus[0].fragile).toBe(true);
    update.flush(craftProduct);
    await Promise.resolve();
    http.expectOne('/api/v1/admin/products?page=0&size=20').flush({
      content: [craftProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    await saving;
    expect(fixture.componentInstance.notice()).toContain('atualizado');
  });
});
