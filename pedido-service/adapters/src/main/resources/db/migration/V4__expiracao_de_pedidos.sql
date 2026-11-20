-- Controle de concorrência otimista do pedido (@Version no PedidoJpa). O job de expiração e o consumidor do
-- pagamento podem tocar no mesmo pedido ao mesmo tempo: sem versão, quem gravasse por último sobrescreveria o outro.
ALTER TABLE pedido ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

-- O job de expiração procura pedidos AGUARDANDO_PAGAMENTO criados há mais de 30 minutos.
CREATE INDEX ix_pedido_status_criado ON pedido (status, criado_em);

-- Trava do job entre instâncias (ShedLock): uma linha por job; quem consegue gravar `lock_until` no futuro roda.
CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
