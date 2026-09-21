import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface Account {
  id: string;
  email: string;
  emailVerified: boolean;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class IdentityService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Account | null>(null);
  private readonly loading = signal(false);

  readonly account = this.current.asReadonly();
  readonly isLoading = this.loading.asReadonly();

  async fetchCsrf(): Promise<void> {
    await firstValueFrom(
      this.http.get<{ token: string }>('/api/v1/csrf', { withCredentials: true }),
    );
  }

  async login(email: string, password: string): Promise<Account> {
    this.loading.set(true);
    try {
      await this.fetchCsrf();
      const account = await firstValueFrom(
        this.http.post<Account>('/api/v1/sessions', { email, password }, { withCredentials: true }),
      );
      this.current.set(account);
      return account;
    } finally {
      this.loading.set(false);
    }
  }

  async fetchCurrent(): Promise<Account | null> {
    try {
      const account = await firstValueFrom(
        this.http.get<Account>('/api/v1/sessions/current', { withCredentials: true }),
      );
      this.current.set(account);
      return account;
    } catch {
      this.current.set(null);
      return null;
    }
  }

  async logout(): Promise<void> {
    await this.fetchCsrf();
    await firstValueFrom(
      this.http.delete<void>('/api/v1/sessions/current', { withCredentials: true }),
    );
    this.current.set(null);
  }

  isAdmin(): boolean {
    return this.current()?.role === 'ADMIN';
  }

  isAuthenticated(): boolean {
    return this.current() !== null;
  }

  setAccount(account: Account | null): void {
    this.current.set(account);
  }
}
