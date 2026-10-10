import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PREPARING'
  | 'READY_FOR_PICKUP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'PICKED_UP'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'UNDER_REVIEW';

type FulfillmentMode = 'DELIVERY' | 'PICKUP';

interface OrderSummary {
  id: string;
  status: OrderStatus;
  mode: FulfillmentMode;
  totalCents: number;
  itemCount: number;
  createdAt: string;
}

interface OrderPage {
  content: OrderSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface OrderItem {
  productName: string;
  skuLabel: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

interface OrderDetails {
  items: OrderItem[];
}

@Component({
  selector: 'app-shipping-admin',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './shipping-admin.component.html',
  styleUrl: './shipping-admin.component.css',
})
export class ShippingAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly pageSize = 50;

  readonly orders = signal<OrderSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly busyOrderId = signal<string | null>(null);
  readonly confirmingOrderId = signal<string | null>(null);
  readonly expandedOrderId = signal<string | null>(null);
  readonly orderDetails = signal<Record<string, OrderDetails>>({});
  readonly loadingDetailsId = signal<string | null>(null);
  readonly detailsError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  pickupCode = '';

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async load(page = this.page()): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const result = await firstValueFrom(
        this.http.get<OrderPage>(`/api/v1/admin/orders?page=${page}&size=${this.pageSize}`, {
          withCredentials: true,
        }),
      );
      this.orders.set(result.content.filter((order) => order.mode === 'PICKUP'));
      this.page.set(result.page);
      this.totalPages.set(result.totalPages);
    } catch {
      this.error.set('Não foi possível carregar as retiradas. Atualize para tentar novamente.');
    } finally {
      this.loading.set(false);
    }
  }

  statusLabel(status: OrderStatus): string {
    return (
      {
        PENDING_PAYMENT: 'Aguardando pagamento',
        PAID: 'Pago',
        PREPARING: 'Em preparação',
        READY_FOR_PICKUP: 'Pronto para retirada',
        IN_TRANSIT: 'Em trânsito',
        DELIVERED: 'Entregue',
        PICKED_UP: 'Retirado',
        CANCELLED: 'Cancelado',
        EXPIRED: 'Expirado',
        UNDER_REVIEW: 'Em análise',
      }[status] ?? status
    );
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }

  async toggleItems(order: OrderSummary): Promise<void> {
    if (this.expandedOrderId() === order.id) {
      this.expandedOrderId.set(null);
      return;
    }
    this.expandedOrderId.set(order.id);
    await this.loadItems(order);
  }

  async loadItems(order: OrderSummary): Promise<void> {
    this.detailsError.set(null);
    if (this.orderDetails()[order.id]) return;

    this.loadingDetailsId.set(order.id);
    try {
      const details = await firstValueFrom(
        this.http.get<OrderDetails>(`/api/v1/admin/orders/${order.id}`, { withCredentials: true }),
      );
      this.orderDetails.update((current) => ({ ...current, [order.id]: details }));
    } catch {
      this.detailsError.set('Não foi possível carregar os itens deste pedido. Tente novamente.');
    } finally {
      this.loadingDetailsId.set(null);
    }
  }

  startConfirmation(order: OrderSummary): void {
    this.error.set(null);
    this.notice.set(null);
    this.pickupCode = '';
    this.confirmingOrderId.set(order.id);
  }

  cancelConfirmation(): void {
    this.confirmingOrderId.set(null);
    this.pickupCode = '';
  }

  async startPreparation(order: OrderSummary): Promise<void> {
    await this.postAction(order, 'prepare', null, 'Preparação iniciada.');
  }

  async markReady(order: OrderSummary): Promise<void> {
    await this.postAction(
      order,
      'ready',
      null,
      'Pedido pronto para retirada. O código permanece com o cliente.',
    );
  }

  async confirmPickup(order: OrderSummary): Promise<void> {
    const code = this.pickupCode.trim().toUpperCase();
    if (!/^[A-Z0-9]{10}$/.test(code)) return;
    await this.postAction(order, 'confirm', { code }, 'Retirada registrada.');
  }

  private async postAction(
    order: OrderSummary,
    action: 'prepare' | 'ready' | 'confirm',
    body: { code: string } | null,
    successMessage: string,
  ): Promise<void> {
    if (this.busyOrderId()) return;
    this.busyOrderId.set(order.id);
    this.error.set(null);
    this.notice.set(null);
    try {
      await firstValueFrom(this.http.get('/api/v1/csrf', { withCredentials: true }));
      await firstValueFrom(
        this.http.post(`/api/v1/admin/orders/${order.id}/pickup/${action}`, body, {
          withCredentials: true,
        }),
      );
      this.notice.set(successMessage);
      this.cancelConfirmation();
      await this.load();
    } catch (failure) {
      const status = (failure as { status?: number }).status;
      this.error.set(
        status === 409
          ? 'O pedido mudou de estado antes da ação. Atualize os pedidos e confira novamente.'
          : action === 'confirm' && status === 422
            ? 'O código não confere ou já foi usado. Peça ao cliente o código atual.'
            : 'Não foi possível registrar a ação. Atualize os pedidos e tente novamente.',
      );
    } finally {
      this.busyOrderId.set(null);
    }
  }
}
