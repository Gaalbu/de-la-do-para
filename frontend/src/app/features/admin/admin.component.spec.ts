import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminComponent } from './admin.component';

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

describe('AdminComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads producers and marks all origin content as demonstration', async () => {
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/producers?page=0&size=20').flush({
      content: [producer],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Sul do Pará (demonstração)',
    );
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Demonstração');
  });

  it('creates a producer and refreshes the list', async () => {
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/producers?page=0&size=20').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.slug = producer.slug;
    component.form.displayName = producer.displayName;
    component.form.originLabel = producer.originLabel;
    component.form.description = producer.description;
    component.save();

    const create = http.expectOne('/api/v1/admin/producers');
    expect(create.request.method).toBe('POST');
    create.flush(producer);
    await Promise.resolve();
    fixture.detectChanges();
    const list = http.expectOne(
      (request) => request.url === '/api/v1/admin/producers' && request.method === 'GET',
    );
    list.flush({ content: [producer], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(producer.displayName);
  });
});
