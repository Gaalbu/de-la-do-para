import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { IdentityService } from './identity.service';

describe('IdentityService account registration', () => {
  let identity: IdentityService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    identity = TestBed.inject(IdentityService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('submits the verification token only to the verification endpoint', async () => {
    const verification = identity.verifyEmail('single-use-token');
    const request = http.expectOne('/api/v1/accounts/verify');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'single-use-token' });
    request.flush({ emailVerified: true });
    await expect(verification).resolves.toEqual({ emailVerified: true });
  });
});
