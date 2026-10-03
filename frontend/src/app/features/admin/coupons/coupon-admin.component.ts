import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  Coupon,
  CouponPage,
  CouponUpdate,
  CouponWrite,
} from '../../../../generated/types.gen';

interface CouponForm {
  code: string;
  discountType: CouponWrite['discountType'];
  discountValue: string;
  minimumAmount: string;
  validFrom: string;
  validUntil: string;
  globalLimit: string | number;
  perEmailLimit: number;
  active: boolean;
}

const localInputValue = (date: Date): string => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
};

const blankForm = (): CouponForm => {
  const now = new Date();
  const end = new Date(now);
  end.setDate(end.getDate() + 30);
  return {
    code: '',
    discountType: 'PERCENTAGE',
    discountValue: '10',
    minimumAmount: '0,00',
    validFrom: localInputValue(now),
    validUntil: localInputValue(end),
    globalLimit: '',
    perEmailLimit: 1,
    active: true,
  };
};

const parseAmountCents = (value: string): number | null => {
  const normalized = value.trim().replace(/\./g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  const [whole, fraction = ''] = normalized.split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
};

const formatAmount = (cents: number): string =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

const localInputFromIso = (value: string): string => localInputValue(new Date(value));

@Component({
  selector: 'app-coupon-admin',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './coupon-admin.component.html',
  styleUrl: './coupon-admin.component.css',
})
export class CouponAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly coupons = signal<Coupon[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly saveError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly editing = signal<Coupon | null>(null);
  form: CouponForm = blankForm();

  ngOnInit(): void {
    void this.loadCoupons();
  }

  async loadCoupons(): Promise<void> {
    this.loading.set(true);
    this.listError.set(null);
    try {
      const result = await firstValueFrom(
        this.http.get<CouponPage>('/api/v1/admin/coupons', {
          params: { page: this.page(), size: 20 },
          withCredentials: true,
        }),
      );
      this.coupons.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      this.listError.set('Não foi possível carregar os cupons. Tente novamente.');
    } finally {
      this.loading.set(false);
    }
  }

  changePage(page: number): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.page.set(page);
    void this.loadCoupons();
  }

  startCreate(): void {
    this.editing.set(null);
    this.form = blankForm();
    this.saveError.set(null);
    this.notice.set(null);
  }

  private resetAfterCreate(): void {
    this.editing.set(null);
    this.form = blankForm();
    this.saveError.set(null);
  }

  startEdit(coupon: Coupon): void {
    this.editing.set(coupon);
    this.form = {
      code: coupon.code,
      discountType: coupon.discountType,
      discountValue:
        coupon.discountType === 'FIXED'
          ? formatAmount(coupon.discountValue).replace('R$', '').trim()
          : String(coupon.discountValue),
      minimumAmount: formatAmount(coupon.minimumCents).replace('R$', '').trim(),
      validFrom: localInputFromIso(coupon.validFrom),
      validUntil: localInputFromIso(coupon.validUntil),
      globalLimit: coupon.globalLimit == null ? '' : String(coupon.globalLimit),
      perEmailLimit: coupon.perEmailLimit,
      active: coupon.active,
    };
    this.saveError.set(null);
    this.notice.set(null);
  }

  discountLabel(coupon: Coupon): string {
    return coupon.discountType === 'PERCENTAGE'
      ? `${coupon.discountValue}%`
      : formatAmount(coupon.discountValue);
  }

  minimumLabel(coupon: Coupon): string {
    return formatAmount(coupon.minimumCents);
  }

  usageLabel(coupon: Coupon): string {
    return coupon.globalLimit == null
      ? `${coupon.globalUsage} usos; sem limite global`
      : `${coupon.globalUsage} de ${coupon.globalLimit} usos`;
  }

  async save(): Promise<void> {
    const discountValue = this.discountValueCents();
    const minimumCents = parseAmountCents(this.form.minimumAmount);
    const globalLimitValue = String(this.form.globalLimit).trim();
    const globalLimit = globalLimitValue ? Number(globalLimitValue) : null;
    const validFrom = new Date(this.form.validFrom);
    const validUntil = new Date(this.form.validUntil);
    if (
      discountValue === null ||
      minimumCents === null ||
      minimumCents < 0 ||
      !Number.isInteger(this.form.perEmailLimit) ||
      this.form.perEmailLimit < 1 ||
      (globalLimit !== null && (!Number.isInteger(globalLimit) || globalLimit < 1)) ||
      Number.isNaN(validFrom.getTime()) ||
      Number.isNaN(validUntil.getTime()) ||
      validUntil <= validFrom
    ) {
      this.saveError.set('Confira desconto, valores, limites e período de validade.');
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);
    this.notice.set(null);
    const editing = this.editing();
    const common = {
      discountType: this.form.discountType,
      discountValue,
      minimumCents,
      validFrom: validFrom.toISOString(),
      validUntil: validUntil.toISOString(),
      globalLimit,
      perEmailLimit: this.form.perEmailLimit,
    };
    try {
      if (editing) {
        const update: CouponUpdate = { ...common, active: this.form.active };
        await firstValueFrom(
          this.http.patch<Coupon>(`/api/v1/admin/coupons/${editing.id}`, update, {
            withCredentials: true,
          }),
        );
        this.notice.set(
          this.form.active ? 'Cupom atualizado.' : 'Cupom desativado; o histórico foi preservado.',
        );
      } else {
        const create: CouponWrite = {
          ...common,
          code: this.form.code.trim().toUpperCase(),
        };
        await firstValueFrom(
          this.http.post<Coupon>('/api/v1/admin/coupons', create, { withCredentials: true }),
        );
        this.notice.set('Cupom criado. Pedidos anteriores mantêm seus valores registrados.');
      }
      await this.loadCoupons();
      if (!this.listError() && !editing) this.resetAfterCreate();
      if (this.listError())
        this.saveError.set('Alteração salva, mas a lista não atualizou. Recarregue.');
    } catch {
      this.saveError.set('Não foi possível salvar o cupom. Confira os dados ou tente novamente.');
    } finally {
      this.saving.set(false);
    }
  }

  private discountValueCents(): number | null {
    if (this.form.discountType === 'PERCENTAGE') {
      const value = Number(this.form.discountValue);
      return Number.isInteger(value) && value >= 1 && value <= 100 ? value : null;
    }
    const value = parseAmountCents(this.form.discountValue);
    return value !== null && value > 0 ? value : null;
  }
}
