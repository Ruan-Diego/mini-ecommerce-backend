-- Código de rastreio, preenchido quando o admin marca o pedido como ENVIADO.
ALTER TABLE pedido ADD COLUMN codigo_rastreio VARCHAR(40) NULL;

-- A lista do admin ordena por criado_em (mais recente primeiro) e filtra por período sem cliente.
CREATE INDEX ix_pedido_criado ON pedido (criado_em);
