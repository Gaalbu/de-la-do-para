import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { IdentityService } from './identity.service';

describe('IdentityService account flow', () => {
  let service: IdentityService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [IdentityService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IdentityService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registers a customer after obtaining a CSRF cookie', async () => {
    const registration = service.register('pessoa@example.com', 'senha-segura-123');
    http.expectOne('/api/v1/csrf').flush({ token: 'csrf' });

    const request = await vi.waitFor(() => http.expectOne('/api/v1/accounts'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'pessoa@example.com',
      password: 'senha-segura-123',
    });
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      id: 'account-1',
      email: 'pessoa@example.com',
      emailVerified: false,
      role: 'CUSTOMER',
    });

    await expect(registration).resolves.toMatchObject({ emailVerified: false, role: 'CUSTOMER' });
  });

  it('submits only the opaque verification token to the public endpoint', async () => {
    const verification = service.verifyEmail('opaque-token');
    const request = http.expectOne('/api/v1/accounts/verify');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'opaque-token' });
    request.flush({
      id: 'account-1',
      email: 'pessoa@example.com',
      emailVerified: true,
      role: 'CUSTOMER',
    });

    await expect(verification).resolves.toMatchObject({ emailVerified: true });
  });
});
