import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { IdentityService } from '../../services/identity.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  const identity = { register: vi.fn() };

  beforeEach(async () => {
    identity.register.mockReset();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideRouter([]), { provide: IdentityService, useValue: identity }],
    }).compileComponents();
  });

  it('creates an unverified customer and asks them to check their email', async () => {
    identity.register.mockResolvedValue({
      id: 'account-1',
      email: 'pessoa@example.com',
      emailVerified: false,
      role: 'CUSTOMER',
    });
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.componentInstance.email = ' pessoa@example.com ';
    fixture.componentInstance.password = 'senha-segura-123';

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(identity.register).toHaveBeenCalledWith('pessoa@example.com', 'senha-segura-123');
    expect(fixture.componentInstance.password).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Confira seu e-mail');
    expect(fixture.nativeElement.textContent).toContain('pessoa@example.com');
  });

  it('explains duplicate email without exposing provider details', async () => {
    identity.register.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.componentInstance.email = 'pessoa@example.com';
    fixture.componentInstance.password = 'senha-segura-123';

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'já está cadastrado',
    );
    expect(fixture.componentInstance.password).toBe('senha-segura-123');
  });

  it('does not call the API with an invalid password length', async () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.componentInstance.email = 'pessoa@example.com';
    fixture.componentInstance.password = 'short';

    await fixture.componentInstance.submit();

    expect(identity.register).not.toHaveBeenCalled();
  });
});
