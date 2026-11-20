-- Pedidos que o pedido-service cancelou, conhecidos pelo evento `pedido.cancelado`. É o que impede cobrar um
-- pedido cancelado. pedido_id aponta para outro serviço: sem chave estrangeira.
CREATE TABLE pedido_cancelado (
    pedido_id     CHAR(36)    NOT NULL,
    cancelado_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_pedido_cancelado PRIMARY KEY (pedido_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
