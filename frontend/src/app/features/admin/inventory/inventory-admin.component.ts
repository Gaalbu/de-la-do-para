import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { IdentityService } from '../../identity/services/identity.service';

type Lot = {
  id: string; skuCode: string; physicalUnits: number; reservedUnits: number;
  freeUnits: number; blocked: boolean; expiresOn: string | null;
  minimumShelfLifeDays: number | null; version: number;
};
type Product = { displayName: string; skus: Array<{ id: string; skuCode: string }> };

@Component({
  selector: 'app-inventory-admin',
  imports: [FormsModule, RouterLink],
  templateUrl: './inventory-admin.component.html',
  styleUrl: './inventory-admin.component.css',
})
export class InventoryAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly identity = inject(IdentityService);
  readonly products = signal<Product[]>([]);
  readonly lots = signal<Lot[]>([]);
  readonly selectedSkuId = signal('');
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  receiving = false;
  physicalUnits = 1;
  expiresOn = '';
  minimumShelfLifeDays: number | null = null;
  receivedAt = new Date().toISOString();

  async ngOnInit(): Promise<void> {
    try {
      const result = await firstValueFrom(this.http.get<{ content: Product[] }>('/api/v1/admin/products?page=0&size=50', { withCredentials: true }));
      this.products.set(result.content);
      const first = result.content[0]?.skus[0]?.id ?? '';
      this.selectedSkuId.set(first);
      if (first) await this.loadLots();
    } catch { this.error.set('Não foi possível carregar os SKUs.'); }
  }

  async loadLots(): Promise<void> {
    if (!this.selectedSkuId()) return;
    try { this.lots.set(await firstValueFrom(this.http.get<Lot[]>(`/api/v1/admin/inventory/skus/${this.selectedSkuId()}/lots`, { withCredentials: true }))); }
    catch { this.error.set('Não foi possível carregar os lotes.'); }
  }

  async receive(): Promise<void> {
    this.receiving = true; this.error.set(null);
    try {
      await firstValueFrom(this.http.post(`/api/v1/admin/inventory/skus/${this.selectedSkuId()}/lots`, {
        physicalUnits: this.physicalUnits, expiresOn: this.expiresOn || null,
        minimumShelfLifeDays: this.minimumShelfLifeDays, receivedAt: this.receivedAt,
      }, { withCredentials: true }));
      this.notice.set('Lote recebido e registrado no histórico.');
      await this.loadLots();
    } catch { this.error.set('Não foi possível registrar o lote.'); }
    finally { this.receiving = false; }
  }

  async adjust(lot: Lot): Promise<void> {
    const value = Number(window.prompt('Nova quantidade física', String(lot.physicalUnits)));
    const reason = window.prompt('Motivo do ajuste');
    if (!Number.isInteger(value) || value < 0 || !reason?.trim()) return;
    try {
      await firstValueFrom(this.http.patch(`/api/v1/admin/inventory/lots/${lot.id}`, {
        actorId: this.identity.account()?.id, physicalUnits: value,
        expectedVersion: lot.version, reason,
      }, { withCredentials: true }));
      this.notice.set('Ajuste registrado.'); await this.loadLots();
    } catch { this.error.set('O lote mudou ou o ajuste foi rejeitado. Recarregue e tente novamente.'); }
  }

  skuOptions(): Array<{ id: string; label: string }> {
    return this.products().flatMap((product) => product.skus.map((sku) => ({ id: sku.id, label: `${product.displayName} · ${sku.skuCode}` })));
  }
}
