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
  readonly imageFile = signal<File | null>(null);
  readonly imagePreview = signal<string | null>(null);
  readonly imageSaving = signal(false);
  readonly imageError = signal<string | null>(null);
  imageAltText = '';
  imageSource = '';
  imageLicense = '';
  imageCreator = '';
  imageAttribution = '';
  imageRightsReviewed = false;
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
    this.clearImageSelection();
    this.imageError.set(null);
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
    this.clearImageSelection();
    this.imageError.set(null);
  }

  selectImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.clearImageSelection();
    this.imageFile.set(file);
    this.imageError.set(null);
    if (file) this.imagePreview.set(URL.createObjectURL(file));
  }

  async uploadImage(): Promise<void> {
    const product = this.editing();
    const file = this.imageFile();
    if (!product || !file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.imageError.set('Escolha um JPEG ou PNG de até 5 MiB.');
      return;
    }
    if (
      !this.imageAltText.trim() ||
      !this.imageSource.trim() ||
      !this.imageLicense.trim() ||
      !this.imageRightsReviewed
    ) {
      this.imageError.set(
        'Preencha texto alternativo, origem, licença e confirme a revisão dos direitos.',
      );
      return;
    }
    const data = new FormData();
    data.set('file', file);
    data.set('altText', this.imageAltText.trim());
    data.set('source', this.imageSource.trim());
    data.set('license', this.imageLicense.trim());
    data.set('creator', this.imageCreator.trim());
    data.set('attribution', this.imageAttribution.trim());
    data.set('rightsReviewed', 'true');
    this.imageSaving.set(true);
    this.imageError.set(null);
    try {
      const image = await firstValueFrom(
        this.http.put<Product['image']>(`/api/v1/admin/products/${product.id}/image`, data, {
          withCredentials: true,
        }),
      );
      this.products.update((items) =>
        items.map((item) => (item.id === product.id ? { ...item, image } : item)),
      );
      this.editing.set({ ...product, image });
      this.notice.set('Imagem principal salva.');
      this.clearImageSelection();
    } catch {
      this.imageError.set(
        'Não foi possível salvar a imagem. Verifique o arquivo e os direitos de uso.',
      );
    } finally {
      this.imageSaving.set(false);
    }
  }

  async removeImage(): Promise<void> {
    const product = this.editing();
    if (!product?.image) return;
    this.imageSaving.set(true);
    this.imageError.set(null);
    try {
      await firstValueFrom(
        this.http.delete(`/api/v1/admin/products/${product.id}/image`, { withCredentials: true }),
      );
      this.products.update((items) =>
        items.map((item) => (item.id === product.id ? { ...item, image: null } : item)),
      );
      this.editing.set({ ...product, image: null });
      this.notice.set('Imagem principal removida.');
    } catch {
      this.imageError.set('Não foi possível remover a imagem. Tente novamente.');
    } finally {
      this.imageSaving.set(false);
    }
  }

  private clearImageSelection(): void {
    const preview = this.imagePreview();
    if (preview) URL.revokeObjectURL(preview);
    this.imagePreview.set(null);
    this.imageFile.set(null);
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
      let saved: Product;
      if (editing) {
        const update: ProductUpdate = { ...payload, active: this.form.active };
        saved = await firstValueFrom(
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
        saved = await firstValueFrom(
          this.http.post<Product>('/api/v1/admin/products', payload, { withCredentials: true }),
        );
        this.notice.set('Produto criado. Conteúdo marcado como demonstração.');
      }
      await this.loadProducts();
      if (this.listError()) {
        this.saveError.set('Alteração salva, mas a lista não foi atualizada. Tente recarregar.');
      } else if (!editing) {
        this.startEdit(saved);
        this.notice.set('Produto criado. Agora você pode adicionar a imagem principal.');
      }
    } catch {
      this.saveError.set('Não foi possível salvar. Confira os dados ou tente novamente.');
    } finally {
      this.saving.set(false);
    }
  }
}
