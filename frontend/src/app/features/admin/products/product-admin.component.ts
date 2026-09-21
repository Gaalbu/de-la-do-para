import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  Product,
  ProductCategory,
  ProductPage,
  ProductSkuWrite,
  ProductUpdate,
  ProductWrite,
  Producer,
  ProducerPage,
} from '../../../../generated/types.gen';

interface ProductForm extends ProductWrite {
  active: boolean;
}

const blankSku = (): ProductSkuWrite => ({
  skuCode: '',
  salesUnit: '',
  netContentGrams: null,
  minimumShelfLifeDays: null,
  fragile: false,
  lengthMm: 1,
  widthMm: 1,
  heightMm: 1,
  grossWeightGrams: 1,
  active: true,
});

const blankForm = (): ProductForm => ({
  slug: '',
  displayName: '',
  description: '',
  category: 'FOOD',
  producerId: '',
  active: true,
  skus: [blankSku()],
});

const categoryLabel = (category: ProductCategory): string =>
  category === 'FOOD' ? 'Alimento' : 'Artesanato';

@Component({
  selector: 'app-product-admin',
  imports: [FormsModule, RouterLink],
  templateUrl: './product-admin.component.html',
  styleUrl: './product-admin.component.css',
})
export class ProductAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly products = signal<Product[]>([]);
  readonly producers = signal<Producer[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly listError = signal<string | null>(null);
  readonly producersError = signal<string | null>(null);
  readonly saveError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly editing = signal<Product | null>(null);
  form: ProductForm = blankForm();

  ngOnInit(): void {
    void this.loadProducers();
    void this.loadProducts();
  }

  selectableProducers(): Producer[] {
    const currentProducerId = this.editing()?.producerId;
    return this.producers().filter(
      (producer) => producer.active || producer.id === currentProducerId,
    );
  }

  producerName(id: string): string {
    return this.producers().find((producer) => producer.id === id)?.displayName ?? 'Produtor';
  }

  categoryLabel(category: ProductCategory): string {
    return categoryLabel(category);
  }

  async loadProducers(): Promise<void> {
    this.producersError.set(null);
    try {
      const result = await firstValueFrom(
        this.http.get<ProducerPage>('/api/v1/admin/producers', {
          params: { page: 0, size: 50 },
          withCredentials: true,
        }),
      );
      this.producers.set(result.content);
    } catch {
      this.producersError.set('Não foi possível carregar produtores. Tente novamente.');
    }
  }

  async loadProducts(): Promise<void> {
    this.loading.set(true);
    this.listError.set(null);
    try {
      const result = await firstValueFrom(
        this.http.get<ProductPage>('/api/v1/admin/products', {
          params: { page: this.page(), size: 20 },
          withCredentials: true,
        }),
      );
      this.products.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      this.listError.set('Não foi possível carregar os produtos. Tente novamente.');
    } finally {
      this.loading.set(false);
    }
  }

  changePage(page: number): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.page.set(page);
    void this.loadProducts();
  }

  startCreate(): void {
    this.editing.set(null);
    this.form = blankForm();
    this.saveError.set(null);
    this.notice.set(null);
  }

  startEdit(product: Product): void {
    this.editing.set(product);
    this.form = {
      slug: product.slug,
      displayName: product.displayName,
      description: product.description,
      category: product.category,
      producerId: product.producerId,
      active: product.active,
      skus: product.skus.map((sku) => ({
        id: sku.id,
        skuCode: sku.skuCode,
        salesUnit: sku.salesUnit,
        netContentGrams: sku.netContentGrams ?? null,
        minimumShelfLifeDays: sku.minimumShelfLifeDays ?? null,
        fragile: sku.fragile,
        lengthMm: sku.lengthMm,
        widthMm: sku.widthMm,
        heightMm: sku.heightMm,
        grossWeightGrams: sku.grossWeightGrams,
        active: sku.active,
      })),
    };
    this.saveError.set(null);
    this.notice.set(null);
  }

  categoryChanged(): void {
    if (this.form.category === 'CRAFT') {
      for (const sku of this.form.skus) {
        sku.netContentGrams = null;
        sku.minimumShelfLifeDays = null;
      }
    }
  }

  addSku(): void {
    this.form.skus.push(blankSku());
  }

  removeSku(index: number): void {
    if (this.form.skus.length > 1) this.form.skus.splice(index, 1);
  }

  confirmDeactivation(): void {
    if (
      globalThis.confirm(
        'Desativar este produto? Ele deixa de aparecer na oferta pública, mas seu histórico será preservado.',
      )
    ) {
      this.form.active = false;
    }
  }

  async save(): Promise<void> {
    this.saving.set(true);
    this.saveError.set(null);
    this.notice.set(null);
    const editing = this.editing();
    const payload: ProductWrite = {
      slug: this.form.slug.trim(),
      displayName: this.form.displayName.trim(),
      description: this.form.description.trim(),
      category: this.form.category,
      producerId: this.form.producerId,
      skus: this.form.skus.map((sku) => ({
        ...(sku.id ? { id: sku.id } : {}),
        skuCode: sku.skuCode.trim(),
        salesUnit: sku.salesUnit.trim(),
        netContentGrams: this.form.category === 'FOOD' ? sku.netContentGrams : null,
        minimumShelfLifeDays: this.form.category === 'FOOD' ? sku.minimumShelfLifeDays : null,
        fragile: sku.fragile,
        lengthMm: sku.lengthMm,
        widthMm: sku.widthMm,
        heightMm: sku.heightMm,
        grossWeightGrams: sku.grossWeightGrams,
        active: sku.active,
      })),
    };
    try {
      if (editing) {
        const update: ProductUpdate = { ...payload, active: this.form.active };
        await firstValueFrom(
          this.http.patch<Product>(`/api/v1/admin/products/${editing.id}`, update, {
            withCredentials: true,
          }),
        );
        this.notice.set(
          this.form.active
            ? 'Produto atualizado.'
            : 'Produto desativado. O cadastro e suas referências foram preservados.',
        );
      } else {
        await firstValueFrom(
          this.http.post<Product>('/api/v1/admin/products', payload, { withCredentials: true }),
        );
        this.notice.set('Produto criado. Conteúdo marcado como demonstração.');
      }
      await this.loadProducts();
      if (this.listError()) {
        this.saveError.set('Alteração salva, mas a lista não foi atualizada. Tente recarregar.');
      } else if (!editing) {
        this.editing.set(null);
        this.form = blankForm();
        this.saveError.set(null);
      }
    } catch {
      this.saveError.set('Não foi possível salvar. Confira os dados ou tente novamente.');
    } finally {
      this.saving.set(false);
    }
  }
}
