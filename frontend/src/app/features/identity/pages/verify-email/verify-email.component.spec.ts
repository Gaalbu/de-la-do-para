import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { IdentityService } from '../../services/identity.service';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  let route: { snapshot: { fragment: string | null } };
  let router: { url: string; navigateByUrl: ReturnType<typeof vi.fn> };
  let identity: { verifyEmail: ReturnType<typeof vi.fn> };

  async function createComponent(fragment: string | null) {
    route = { snapshot: { fragment } };
    router = {
      url: fragment === null ? '/verify-email' : `/verify-email#${fragment}`,
      navigateByUrl: vi.fn().mockResolvedValue(true),
    };
    identity = { verifyEmail: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        { provide: ActivatedRoute, useValue: route },
        { provide: Router, useValue: router },
        { provide: IdentityService, useValue: identity },
      ],
    }).compileComponents();
    return TestBed.createComponent(VerifyEmailComponent);
  }

  it('removes the fragment from browser history before verifying its token', async () => {
    const fixture = await createComponent('token=opaque-token');
    identity.verifyEmail.mockResolvedValue({
      id: 'account-1',
      email: 'pessoa@example.com',
      emailVerified: true,
      role: 'CUSTOMER',
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await vi.waitFor(() =>
      expect(fixture.nativeElement.textContent).toContain('E-mail confirmado'),
    );

    expect(router.navigateByUrl).toHaveBeenCalledWith('/verify-email', {
      replaceUrl: true,
    });
    expect(identity.verifyEmail).toHaveBeenCalledWith('opaque-token');
  });

  it('does not call the API when the fragment has no token', async () => {
    const fixture = await createComponent('other=value');

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(identity.verifyEmail).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('não contém um código');
  });

  it('does not submit the token if the URL cannot be cleaned', async () => {
    const fixture = await createComponent('token=opaque-token');
    router.navigateByUrl.mockRejectedValue(new Error('navigation failed'));

    fixture.detectChanges();
    await vi.waitFor(() =>
      expect(fixture.nativeElement.textContent).toContain('Não foi possível confirmar'),
    );

    expect(identity.verifyEmail).not.toHaveBeenCalled();
  });

  it('explains that an expired or already used token cannot be reused', async () => {
    const fixture = await createComponent('token=expired-token');
    identity.verifyEmail.mockRejectedValue(new HttpErrorResponse({ status: 410 }));

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    await vi.waitFor(() =>
      expect(fixture.nativeElement.textContent).toContain('expirou ou já foi usado'),
    );
  });
});
