import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

export type PaymentOperation = {
  id: string;
  kind: 'CREATE_CHECKOUT' | 'REFUND' | 'QUERY';
  status: string;
  result: string | null;
  createdAt: string;
  finishedAt: string | null;
  requestedBy: string | null;
  requestReason: string | null;
};
export type PaymentAttention = {
  intent: {
    id: string;
    orderId: string;
    amountCents: number;
    status: 'UNKNOWN' | 'UNDER_REVIEW' | 'REFUND_REQUESTED';
    reason: string | null;
    updatedAt: string;
  };
  operations: PaymentOperation[];
};
export type QuarantinedEvent = {
  topic: string;
  partition: number;
  offset: number;
  failureKind: 'TRANSIENT' | 'INVALID';
  attempts: number;
  lastError: string;
  eventId: string | null;
  correlationId: string | null;
  quarantinedAt: string;
  replayStatus: string | null;
  replayResult: string | null;
  replayRequestedBy: string | null;
  replayRequestedAt: string | null;
};

/** What the operator is about to ask for; the form shows its effect before anything is sent. */
type PendingAction =
  { kind: 'lookup'; payment: PaymentAttention } | { kind: 'replay'; event: QuarantinedEvent };

/**
 * Recovery console (C83): uncertain payments with their full provider trail and quarantined events. Every action
 * only queues work for the worker, needs a reason and states its effect before it is confirmed.
 */
@Component({
  selector: 'app-operations-admin',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './operations-admin.component.html',
  styleUrl: './operations-admin.component.css',
})
export class OperationsAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly payments = signal<PaymentAttention[]>([]);
  readonly events = signal<QuarantinedEvent[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly pending = signal<PendingAction | null>(null);
  readonly sending = signal(false);
  reason = '';

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [payments, events] = await Promise.all([
        firstValueFrom(
          this.http.get<PaymentAttention[]>('/api/v1/admin/payments/attention', {
            withCredentials: true,
          }),
        ),
        firstValueFrom(
          this.http.get<QuarantinedEvent[]>('/api/v1/admin/events/quarantine', {
            withCredentials: true,
          }),
        ),
      ]);
      this.payments.set(payments);
      this.events.set(events);
    } catch {
      this.error.set('Não foi possível carregar os incidentes. Tente atualizar.');
    } finally {
      this.loading.set(false);
    }
  }

  start(action: PendingAction): void {
    this.pending.set(action);
    this.reason = '';
    this.notice.set(null);
    this.error.set(null);
  }

  cancel(): void {
    this.pending.set(null);
  }

  effect(action: PendingAction): string {
    if (action.kind === 'replay') {
      return 'O worker consome de novo o evento original, com o mesmo identificador e as regras normais. Se o efeito já foi aplicado, nada muda; se o handler falhar de novo, o registro continua em quarentena.';
    }
    if (action.payment.intent.status === 'REFUND_REQUESTED') {
      return 'O worker consulta o reembolso no provedor. Nenhum reembolso novo é criado; o pagamento só passa a reembolsado se o provedor mostrar o reembolso concluído.';
    }
    return 'O worker consulta o provedor. Nenhuma cobrança nova é criada: pagamento confirmado com o valor exato confirma o pagamento, valor divergente vai para análise e ausência de resposta não muda nada.';
  }

  async confirm(): Promise<void> {
    const action = this.pending();
    const reason = this.reason.trim();
    if (!action || !reason || this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.get('/api/v1/csrf', { withCredentials: true }));
      const url =
        action.kind === 'lookup'
          ? `/api/v1/admin/payments/${action.payment.intent.id}/lookups`
          : `/api/v1/admin/events/quarantine/${encodeURIComponent(action.event.topic)}/${action.event.partition}/${action.event.offset}/replays`;
      const result = await firstValueFrom(
        this.http.post<{ created: boolean }>(url, { reason }, { withCredentials: true }),
      );
      this.notice.set(
        result.created
          ? 'Pedido registrado. O worker executa em instantes; atualize para ver o resultado.'
          : 'Já existia um pedido igual aguardando o worker; ele foi mantido.',
      );
      this.pending.set(null);
      await this.load();
    } catch (failure) {
      const status = (failure as { status?: number }).status;
      this.error.set(
        status === 409
          ? 'A situação mudou e a ação não é mais possível. Atualize a lista.'
          : 'Não foi possível registrar o pedido. Tente novamente.',
      );
    } finally {
      this.sending.set(false);
    }
  }

  paymentStatus(status: string): string {
    return (
      {
        UNKNOWN: 'Resultado desconhecido',
        UNDER_REVIEW: 'Em análise',
        REFUND_REQUESTED: 'Reembolso em andamento',
      }[status] ?? status
    );
  }

  operationKind(kind: string): string {
    return (
      { CREATE_CHECKOUT: 'Criação do checkout', REFUND: 'Reembolso', QUERY: 'Consulta' }[kind] ??
      kind
    );
  }

  formatPrice(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(
      cents / 100,
    );
  }
}
