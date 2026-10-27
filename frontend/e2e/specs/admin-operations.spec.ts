import { expect, test } from '@playwright/test';

const unknownPayment = {
  intent: {
    id: '9a1c0f62-0d5e-4a4f-9a8b-5e2b8a1d0c01',
    orderId: '7d0c1c5e-6f4a-4d43-8a51-3f3a1f8b2e10',
    amountCents: 5250,
    status: 'UNKNOWN',
    reason: 'OUTCOME_UNKNOWN',
    updatedAt: '2026-10-05T12:00:00Z',
  },
  operations: [
    {
      id: 'op-1',
      kind: 'CREATE_CHECKOUT',
      status: 'UNKNOWN',
      result: 'UNKNOWN:SocketTimeoutException',
      createdAt: '2026-10-05T11:59:00Z',
      finishedAt: '2026-10-05T12:00:00Z',
      requestedBy: null,
      requestReason: null,
    },
  ],
};

const quarantined = (eventId: string | null) => ({
  topic: 'events.inbound',
  partition: 0,
  offset: 42,
  failureKind: eventId ? 'TRANSIENT' : 'INVALID',
  attempts: eventId ? 8 : 1,
  lastError: 'IllegalStateException',
  eventId,
  correlationId: 'c0ffee00-0000-4000-8000-000000000042',
  quarantinedAt: '2026-10-05T12:05:00Z',
  replayStatus: null,
  replayResult: null,
  replayRequestedBy: null,
  replayRequestedAt: null,
});

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/sessions/current', (route) =>
    route.fulfill({
      json: { id: 'admin', email: 'admin@example.test', emailVerified: true, role: 'ADMIN' },
    }),
  );
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 't' } }));
});

test('admin queues an audited lookup after reading its effect and sees who asked', async ({
  page,
}) => {
  let payments = [unknownPayment];
  const bodies: unknown[] = [];
  await page.route('**/api/v1/admin/events/quarantine', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/v1/admin/payments/attention', (route) =>
    route.fulfill({ json: payments }),
  );
  await page.route('**/api/v1/admin/payments/*/lookups', async (route) => {
    bodies.push(route.request().postDataJSON());
    payments = [
      {
        ...unknownPayment,
        operations: [
          {
            id: 'op-2',
            kind: 'QUERY',
            status: 'PENDING',
            result: null,
            createdAt: '2026-10-05T12:10:00Z',
            finishedAt: null,
            requestedBy: 'admin@example.test',
            requestReason: 'Cliente enviou comprovante Pix',
          },
          ...unknownPayment.operations,
        ],
      },
    ];
    await route.fulfill({ status: 202, json: { operationId: 'op-2', created: true } });
  });

  await page.goto('/admin/operations');
  const incident = page.getByRole('article', { name: /Pagamento do pedido/ });
  await expect(incident).toContainText('Resultado desconhecido');
  await expect(incident).toContainText('UNKNOWN:SocketTimeoutException');

  await incident.getByRole('button', { name: 'Pedir nova consulta' }).click();
  await expect(page.getByText(/Nenhuma cobrança nova é criada/)).toBeVisible();
  const confirm = page.getByRole('button', { name: 'Confirmar pedido' });
  await expect(confirm).toBeDisabled();
  await page.getByLabel(/Motivo/).fill('Cliente enviou comprovante Pix');
  await confirm.click();

  await expect(page.getByRole('status')).toContainText('Pedido registrado');
  await expect(incident).toContainText('admin@example.test — Cliente enviou comprovante Pix');
  expect(bodies).toEqual([{ reason: 'Cliente enviou comprovante Pix' }]);
});

test('replay is offered only for readable events and reports a changed situation', async ({
  page,
}) => {
  await page.route('**/api/v1/admin/payments/attention', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/v1/admin/events/quarantine', (route) =>
    route.fulfill({
      json: [
        quarantined('5b0f0b8e-6f8a-4b8e-9d5a-0d7b1a2c3d4e'),
        { ...quarantined(null), offset: 43 },
      ],
    }),
  );
  await page.route('**/api/v1/admin/events/quarantine/*/*/*/replays', (route) =>
    route.fulfill({ status: 409, contentType: 'application/problem+json', json: {} }),
  );

  await page.goto('/admin/operations');
  await expect(page.getByText('Nenhum pagamento incerto')).toBeVisible();
  const invalid = page.getByRole('article', { name: 'Evento events.inbound 0/43' });
  await expect(invalid).toContainText('Replay indisponível');
  await expect(invalid.getByRole('button')).toHaveCount(0);

  const readable = page.getByRole('article', { name: 'Evento events.inbound 0/42' });
  await expect(readable).toContainText('c0ffee00-0000-4000-8000-000000000042');
  await readable.getByRole('button', { name: 'Pedir replay' }).click();
  await expect(page.getByText(/mesmo identificador e as regras normais/)).toBeVisible();
  await page.getByLabel(/Motivo/).fill('Banco voltou');
  await page.getByRole('button', { name: 'Confirmar pedido' }).click();
  await expect(page.getByRole('alert')).toContainText('A situação mudou');
});
