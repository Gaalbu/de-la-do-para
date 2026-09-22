import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

type Category = 'FOOD' | 'CRAFT';
type Sort = 'RELEVANCE' | 'PRICE_ASC' | 'PRICE_DESC' | 'NAME_ASC';

interface StorefrontSku {
  skuCode: string;
  salesUnit: string;
  priceCents: number;
  availableUnits: number;
}

interface StorefrontProduct {
  slug: string;
  displayName: string;
  category: Category;
  producer: { displayName: string; originLabel: string };
  skus: StorefrontSku[];
}

interface StorefrontPage {
  content: StorefrontProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Component({
  selector: 'app-storefront',
  styleUrl: './storefront.component.css',
  templateUrl: './storefront.component.html',
})
export class StorefrontComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly products = signal<StorefrontProduct[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly page = signal(0);
  readonly producer = signal('');
  readonly category = signal<Category | ''>('');
  readonly minPriceCents = signal<number | null>(null);
  readonly maxPriceCents = signal<number | null>(null);
  readonly sort = signal<Sort>('RELEVANCE');

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.producer.set(params.get('producer') ?? '');
      this.category.set((params.get('category') as Category | null) ?? '');
      this.minPriceCents.set(this.parsePrice(params.get('minPriceCents')));
      this.maxPriceCents.set(this.parsePrice(params.get('maxPriceCents')));
      this.sort.set((params.get('sort') as Sort | null) ?? 'RELEVANCE');
      this.page.set(Number(params.get('page') ?? 0));
      void this.load();
    });
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      const response = await firstValueFrom(
        this.http.get<StorefrontPage>('/api/v1/products', {
          params: this.queryParams(),
        }),
      );
      this.products.set(response.content);
      this.totalElements.set(response.totalElements);
      this.totalPages.set(response.totalPages);
      this.page.set(response.page);
    } catch {
      this.products.set([]);
      this.totalElements.set(0);
      this.totalPages.set(0);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  async applyFilters(form: HTMLFormElement): Promise<void> {
    const data = new FormData(form);
    const queryParams: Record<string, string | number | null> = {
      producer: this.clean(data.get('producer')),
      category: this.clean(data.get('category')),
      minPriceCents: this.toCents(data.get('minPrice')),
      maxPriceCents: this.toCents(data.get('maxPrice')),
      sort: this.clean(data.get('sort')) || 'RELEVANCE',
      page: 0,
    };
    await this.router.navigate(['/'], { queryParams });
  }

  async clearFilters(): Promise<void> {
    await this.router.navigate(['/'], { queryParams: {} });
  }

  async goToPage(nextPage: number): Promise<void> {
    if (nextPage < 0 || nextPage >= this.totalPages()) return;
    await this.router.navigate(['/'], { queryParams: { ...this.currentQuery(), page: nextPage } });
  }

  lowestPrice(product: StorefrontProduct): number {
    return Math.min(...product.skus.map((sku) => sku.priceCents));
  }

  categoryLabel(category: Category): string {
    return category === 'FOOD' ? 'Alimentos' : 'Artesanato';
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }

  private queryParams(): Record<string, string | number> {
    return Object.fromEntries(
      Object.entries({
        producer: this.producer(),
        category: this.category(),
        minPriceCents: this.minPriceCents(),
        maxPriceCents: this.maxPriceCents(),
        sort: this.sort(),
        page: this.page(),
        size: 12,
      }).filter(([, value]) => value !== '' && value !== null),
    ) as Record<string, string | number>;
  }

  private currentQuery(): Record<string, string | number> {
    return this.queryParams();
  }

  private parsePrice(value: string | null): number | null {
    const parsed = value === null ? NaN : Number(value);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
  }

  private toCents(value: FormDataEntryValue | null): number | null {
    const parsed = value === null ? NaN : Number(String(value).replace(',', '.')) * 100;
    return Number.isFinite(parsed) && parsed >= 0 ? Math.round(parsed) : null;
  }

  private clean(value: FormDataEntryValue | null): string | null {
    const cleaned = String(value ?? '').trim();
    return cleaned === '' ? null : cleaned;
  }
}
