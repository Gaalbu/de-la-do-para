import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CouponAdminComponent } from './coupon-admin.component';

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('CouponAdminComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CouponAdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads coupon rules and explains their current availability', async () => {
    const fixture = TestBed.createComponent(CouponAdminComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/coupons?page=0&size=20').flush({
      content: [
        {
          id: 'coupon-1',
          code: 'BEMVINDO',
          discountType: 'PERCENTAGE',
          discountValue: 10,
          minimumCents: 5000,
          validFrom: '2026-09-01T12:00:00Z',
          validUntil: '2026-10-01T12:00:00Z',
          active: true,
          globalLimit: 50,
          perEmailLimit: 1,
          globalUsage: 12,
          createdAt: '2026-09-01T12:00:00Z',
          updatedAt: '2026-09-01T12:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('BEMVINDO');
    expect(text).toContain('R$\u00a050,00');
    expect(text).toContain('12 de 50');
  });

  it('creates a coupon using normalized code and integer cent values', async () => {
    const fixture = TestBed.createComponent(CouponAdminComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/coupons?page=0&size=20').flush(emptyPage);
    await fixture.whenStable();

    const component = fixture.componentInstance;
    component.form.code = ' bemvindo ';
    component.form.discountType = 'FIXED';
    component.form.discountValue = '12,50';
    component.form.minimumAmount = '75,00';
    component.form.validFrom = '2026-09-25T09:00';
    component.form.validUntil = '2026-10-25T18:00';
    component.form.globalLimit = '20';
    component.form.perEmailLimit = 1;
    const saving = component.save();

    const create = http.expectOne('/api/v1/admin/coupons');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toMatchObject({
      code: 'BEMVINDO',
      discountType: 'FIXED',
      discountValue: 1250,
      minimumCents: 7500,
      globalLimit: 20,
    });
    create.flush({});
    await Promise.resolve();
    http.expectOne('/api/v1/admin/coupons?page=0&size=20').flush(emptyPage);
    await saving;

    expect(component.notice()).toContain('criado');
  });
});
