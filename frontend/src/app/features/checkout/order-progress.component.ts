import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface OrderProgress {
  id: string;
  status: string;
  totalCents: number;
  items: Array<{ productName: string; quantity: number; lineTotalCents: number }>;
  payment?: {
    status: string;
    checkoutUrl: string | null;
    checkoutExpiresAt: string | null;
  } | null;
}

@Component({
  selector: 'app-order-progress',
  imports: [RouterLink],
  template: `
    <main class="progress" aria-labelledby="progress-title">
      <p><a routerLink="/">← Voltar à loja</a></p>
      <h1 id="progress-title">Acompanhe seu pedido</h1>
      @if (loading() && !order()) {
        <p role="status">Carregando o pedido…</p>
      } @else if (error()) {
        <p role="alert">
          Não foi possível carregar este pedido. Confira o acesso e tente novamente.
        </p>
        <button type="button" (click)="load()">Tentar novamente</button>
      } @else if (order(); as current) {
        <p class="status" role="status">{{ orderStatus(current.status) }}</p>
        <p>
          Pedido <strong>{{ current.id }}</strong>
        </p>
        <ul>
          @for (item of current.items; track $index) {
            <li>{{ item.quantity }} × {{ item.productName }}</li>
          }
        </ul>
        <p>
          Total: <strong>{{ formatPrice(current.totalCents) }}</strong>
        </p>
        @if (current.status === 'PAID') {
          <h2>Pagamento confirmado</h2>
          <p>Seu pedido foi confirmado. Acompanhe as próximas atualizações por aqui.</p>
        } @else if (current.status === 'PENDING_PAYMENT') {
          @if (current.payment?.status === 'DECLINED') {
            <p role="alert">O pagamento não foi aprovado.</p>
            @if (current.payment.checkoutUrl; as checkoutUrl) {
              <a class="pay" [href]="checkoutUrl" target="_blank" rel="noopener">
                Revisar pagamento
              </a>
            }
          } @else if (current.payment?.status === 'UNKNOWN') {
            <p role="status">
              Estamos confirmando o resultado do pagamento. Não repita a cobrança; atualizaremos o
              pedido assim que o provedor responder.
            </p>
          } @else if (current.payment?.checkoutUrl; as checkoutUrl) {
            <a class="pay" [href]="checkoutUrl" target="_blank" rel="noopener">
              Continuar para o pagamento
            </a>
          } @else {
            <p>
              Estamos preparando as opções de pagamento. Esta página será atualizada
              automaticamente.
            </p>
          }
          <p>O pedido só será confirmado depois da validação do pagamento.</p>
        } @else if (current.status === 'CANCELLED') {
          <h2>Pedido cancelado</h2>
        } @else if (current.status === 'UNDER_REVIEW') {
          <h2>Pedido em análise</h2>
          <p>Nossa equipe vai revisar o pedido e registrar a decisão aqui.</p>
        } @else {
          <p>{{ orderStatus(current.status) }}</p>
        }
        @if (refundMessage(current); as message) {
          <p class="refund" role="status">{{ message }}</p>
        }
        @if (cancellable(current)) {
          <section class="cancel" aria-labelledby="cancel-title">
            <h2 id="cancel-title">Cancelamento</h2>
            @if (confirming()) {
              <p>{{ cancelNotice(current) }}</p>
              <button type="button" (click)="cancel()" [disabled]="cancelling()">
                {{ cancelling() ? 'Enviando…' : 'Confirmar cancelamento' }}
              </button>
              <button
                type="button"
                class="secondary"
                (click)="confirming.set(false)"
                [disabled]="cancelling()"
              >
                Manter pedido
              </button>
            } @else {
              <button type="button" class="secondary" (click)="confirming.set(true)">
                {{ current.status === 'IN_TRANSIT' ? 'Solicitar cancelamento' : 'Cancelar pedido' }}
              </button>
            }
          </section>
        }
        @if (cancelError()) {
          <p role="alert">{{ cancelError() }}</p>
        }
        <button type="button" (click)="load()" [disabled]="loading()">Atualizar status</button>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
    }
    .progress {
      max-width: 760px;
      margin: 0 auto;
      padding: 3.5rem 1.25rem 5rem;
    }
    h1 {
      color: var(--color-ink);
      font-size: clamp(2.4rem, 7vw, 5rem);
      letter-spacing: -0.05em;
    }
    .status {
      padding: 1.25rem;
      border: 1px dashed var(--color-line);
    }
    .pay,
    button {
      display: inline-block;
      margin: 0.75rem 0;
      padding: 0.75rem 1rem;
      border: 1px solid var(--color-line);
      border-radius: 0.2rem;
      background: var(--color-ink);
      color: var(--color-surface);
      font: inherit;
    }
    button {
      cursor: pointer;
    }
    button.secondary {
      margin-right: 0.5rem;
      background: var(--color-surface);
      color: var(--color-ink);
    }
    .refund {
      padding: 1rem;
      border-left: 3px solid var(--color-accent, var(--color-ink));
    }
  `,
})
export class OrderProgressComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);
  readonly order = signal<OrderProgress | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly confirming = signal(false);
  readonly cancelling = signal(false);
  readonly cancelError = signal<string | null>(null);
  private orderId = '';
  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';
    void this.load();
    this.timer = setInterval(() => {
      const current = this.order();
      if (current?.status === 'PENDING_PAYMENT' || current?.payment?.status === 'REFUND_REQUESTED')
        void this.load();
    }, 5000);
  }

  ngOnDestroy(): void {
    if (this.timer !== null) clearInterval(this.timer);
  }

  async load(): Promise<void> {
    if (this.loading()) return;
    if (!this.orderId) {
      this.error.set(true);
      return;
    }
    this.loading.set(true);
    this.error.set(false);
    try {
      this.order.set(
        await firstValueFrom(
          this.http.get<OrderProgress>(`/api/v1/orders/${this.orderId}`, {
            headers: this.accessHeaders(),
          }),
        ),
      );
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Asks the API to cancel; the answer is the order's status after the request, so a repeated click or a reload keeps
   * the first outcome. The order is reloaded afterwards to show the refund progress the backend recorded.
   */
  async cancel(): Promise<void> {
    if (this.cancelling()) return;
    this.cancelling.set(true);
    this.cancelError.set(null);
    try {
      await firstValueFrom(this.http.get('/api/v1/csrf', { withCredentials: true }));
      await firstValueFrom(
        this.http.post(
          `/api/v1/orders/${this.orderId}/cancellation`,
          {},
          { headers: this.accessHeaders(), withCredentials: true },
        ),
      );
      this.confirming.set(false);
    } catch (failure) {
      this.cancelError.set(
        (failure as { status?: number }).status === 409
          ? 'Este pedido não pode mais ser cancelado. Atualize o status para ver a situação atual.'
          : 'Não foi possível enviar o cancelamento. Tente novamente.',
      );
    } finally {
      this.cancelling.set(false);
    }
    await this.load();
  }

  cancellable(order: OrderProgress): boolean {
    if (order.status === 'PENDING_PAYMENT') return order.payment?.status !== 'CONFIRMED';
    return ['PAID', 'PREPARING', 'READY_FOR_PICKUP', 'IN_TRANSIT'].includes(order.status);
  }

  cancelNotice(order: OrderProgress): string {
    if (order.status === 'IN_TRANSIT') {
      return 'O pedido já está com a transportadora. A solicitação vai para análise e não gera reembolso automático.';
    }
    if (order.status === 'PENDING_PAYMENT') {
      return 'O pedido ainda não foi pago. Os itens voltam ao estoque e nenhuma cobrança será feita.';
    }
    return 'O pagamento será reembolsado integralmente. O reembolso é processado pelo provedor e aparece aqui quando concluído.';
  }

  refundMessage(order: OrderProgress): string | null {
    return (
      {
        REFUND_REQUESTED: 'Reembolso solicitado. Estamos aguardando a confirmação do provedor.',
        REFUNDED: 'Reembolso integral concluído.',
        UNDER_REVIEW: 'O pagamento está em análise pela nossa equipe.',
      }[order.payment?.status ?? ''] ?? null
    );
  }

  private accessHeaders(): Record<string, string> {
    const token = this.document.defaultView?.sessionStorage.getItem(
      `dlp.order-token.${this.orderId}`,
    );
    return token ? { 'X-Order-Token': token } : {};
  }

  orderStatus(status: string): string {
    return (
      {
        PENDING_PAYMENT: 'Aguardando pagamento',
        PAID: 'Pagamento confirmado',
        EXPIRED: 'Prazo de pagamento encerrado',
        UNDER_REVIEW: 'Pedido em análise',
        CANCELLED: 'Pedido cancelado',
        PREPARING: 'Pedido em preparação',
        READY_FOR_PICKUP: 'Pronto para retirada',
        PICKED_UP: 'Pedido retirado',
        IN_TRANSIT: 'Pedido com a transportadora',
        DELIVERED: 'Pedido entregue',
      }[status] ?? 'Status do pedido atualizado'
    );
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }
}
