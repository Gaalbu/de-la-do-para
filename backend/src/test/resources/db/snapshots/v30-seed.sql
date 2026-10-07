-- Dados fictícios para um banco migrado até a V30, primeira versão com a cadeia
-- completa conta → catálogo → estoque → pedido → pagamento → outbox (C93).
-- Provam que migrations posteriores preservam dados existentes; sem dados reais.
INSERT INTO accounts (id, email, password_hash, role) VALUES
  ('00000000-0000-4000-8000-000000000001', 'admin@example.test', '$2a$12$fixture.hash.not.a.real.credential.for.tests.only', 'ADMIN'),
  ('00000000-0000-4000-8000-000000000002', 'cliente@example.test', '$2a$12$fixture.hash.not.a.real.credential.for.tests.only', 'CUSTOMER');

INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000010', 'produtor-fixture', 'Produtor Fixture', 'Origem fictícia, Pará', 'Produtor de demonstração.', now(), now());

INSERT INTO products (id, slug, display_name, description, category, producer_id, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000020', 'farinha-fixture', 'Farinha Fixture', 'Alimento de demonstração.', 'FOOD', '00000000-0000-4000-8000-000000000010', now(), now()),
  ('00000000-0000-4000-8000-000000000021', 'cesto-fixture', 'Cesto Fixture', 'Artesanato de demonstração.', 'CRAFT', '00000000-0000-4000-8000-000000000010', now(), now());

INSERT INTO product_skus (id, product_id, product_category, sku_code, sales_unit, net_content_grams, minimum_shelf_life_days, fragile, length_mm, width_mm, height_mm, gross_weight_grams, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000030', '00000000-0000-4000-8000-000000000020', 'FOOD', 'FARINHA-1KG', 'pacote 1 kg', 1000, 30, false, 200, 100, 50, 1100, now(), now()),
  ('00000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000021', 'CRAFT', 'CESTO-M', 'unidade', NULL, NULL, true, 300, 300, 200, 400, now(), now());

INSERT INTO pricing_sku_prices (sku_id, unit_price_cents, currency, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000030', 2500, 'BRL', now()),
  ('00000000-0000-4000-8000-000000000031', 8900, 'BRL', now());

INSERT INTO inventory_lots (id, sku_id, physical_units, reserved_units, expires_on, minimum_shelf_life_days, received_at, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000040', '00000000-0000-4000-8000-000000000030', 20, 2, current_date + 180, 30, now(), now(), now()),
  ('00000000-0000-4000-8000-000000000041', '00000000-0000-4000-8000-000000000031', 5, 0, NULL, NULL, now(), now(), now());

INSERT INTO purchase_order (id, checkout_key, account_id, contact_email, fulfillment_mode, currency, subtotal_cents, shipping_cents, discount_cents, total_cents, preparation_days, pricing_rule_version, destination, status, status_sequence, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000050', 'fixture-checkout-key-1', '00000000-0000-4000-8000-000000000002', 'cliente@example.test', 'DELIVERY', 'BRL', 5000, 1500, 0, 6500, 2, 'fixture-1', '{"city": "Belém", "state": "PA"}', 'PENDING_PAYMENT', 0, now(), now());

INSERT INTO purchase_order_item (order_id, line_no, sku_id, product_name, sku_label, quantity, unit_price_cents, line_total_cents) VALUES
  ('00000000-0000-4000-8000-000000000050', 1, '00000000-0000-4000-8000-000000000030', 'Farinha Fixture', 'pacote 1 kg', 2, 2500, 5000);

INSERT INTO payment_intent (id, order_id, amount_cents, currency, status, status_version, created_at, updated_at) VALUES
  ('00000000-0000-4000-8000-000000000060', '00000000-0000-4000-8000-000000000050', 6500, 'BRL', 'AWAITING_PAYMENT', 1, now(), now());

INSERT INTO event_outbox (event_id, event_type, schema_version, aggregate_id, aggregate_version, occurred_at, correlation_id, causation_id, payload, available_at, created_at) VALUES
  ('00000000-0000-4000-8000-000000000070', 'order.created', 1, '00000000-0000-4000-8000-000000000050', 0, now(), '00000000-0000-4000-8000-000000000071', '00000000-0000-4000-8000-000000000072', '{"orderId": "00000000-0000-4000-8000-000000000050"}', now(), now());
