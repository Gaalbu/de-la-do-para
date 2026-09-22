import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CartService } from '../cart/cart.service';

interface PublicSku {
  id: string;
  skuCode: string;
  salesUnit: string;
  netContentGrams: number | null;
  minimumShelfLifeDays: number | null;
  priceCents: number | null;
  availableUnits: number;
}

interface PublicProduct {
  slug: string;
  displayName: string;
  description: string;
  category: 'FOOD' | 'CRAFT';
  image: { url: string; altText: string } | null;
  producer: { displayName: string; originLabel: string; description: string };
  skus: PublicSku[];
}

@Component({
  imports: [RouterLink],
  selector: 'app-product-detail',
  styleUrl: './product-detail.component.css',
  templateUrl: './product-detail.component.html',
})
export class ProductDetailComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly cartService = inject(CartService);

  readonly product = signal<PublicProduct | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => void this.load(params.get('slug') ?? ''));
  }

  async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      this.product.set(
        await firstValueFrom(this.http.get<PublicProduct>(`/api/v1/products/${slug}`)),
      );
    } catch {
      this.product.set(null);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  formatPrice(cents: number | null): string {
    return cents === null
      ? 'Preço indisponível'
      : new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
  }

  async addToCart(skuId: string): Promise<void> {
    if (!this.cartService.cart()) await this.cartService.load();
    await this.cartService.add(skuId);
  }
}
