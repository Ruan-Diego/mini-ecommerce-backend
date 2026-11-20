-- Schema do pedido-service. Ids são UUID em texto (CHAR(36)), como nos outros serviços.
-- O pedido guarda uma foto do que foi comprado (nome, preço, frete, endereço): nada aqui aponta para o
-- catálogo, que é outro serviço, por isso não há chave estrangeira para produto.

CREATE TABLE pedido (
    id               CHAR(36)       NOT NULL,
    cliente_id       VARCHAR(64)    NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    frete_codigo     VARCHAR(30)    NOT NULL,
    frete_nome       VARCHAR(60)    NOT NULL,
    frete_valor      DECIMAL(12, 2) NOT NULL,
    frete_prazo_dias INT            NOT NULL,
    end_destinatario VARCHAR(120)   NOT NULL,
    end_cep          CHAR(8)        NOT NULL,
    end_logradouro   VARCHAR(160)   NOT NULL,
    end_numero       VARCHAR(20)    NOT NULL,
    end_complemento  VARCHAR(80)    NULL,
    end_bairro       VARCHAR(80)    NOT NULL,
    end_cidade       VARCHAR(80)    NOT NULL,
    end_uf           CHAR(2)        NOT NULL,
    criado_em        DATETIME(6)    NOT NULL,
    atualizado_em    DATETIME(6)    NOT NULL,
    CONSTRAINT pk_pedido PRIMARY KEY (id),
    CONSTRAINT ck_pedido_status CHECK (status IN
        ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT ck_pedido_frete_valor CHECK (frete_valor >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pedido_cliente_criado ON pedido (cliente_id, criado_em);

CREATE TABLE item_pedido (
    pedido_id       CHAR(36)       NOT NULL,
    posicao         INT            NOT NULL,
    produto_id      CHAR(36)       NOT NULL,
    nome            VARCHAR(120)   NOT NULL,
    preco_unitario  DECIMAL(12, 2) NOT NULL,
    quantidade      INT            NOT NULL,
    CONSTRAINT pk_item_pedido PRIMARY KEY (pedido_id, posicao),
    CONSTRAINT fk_item_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT ck_item_pedido_preco_positivo CHECK (preco_unitario > 0),
    CONSTRAINT ck_item_pedido_quantidade CHECK (quantidade BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Outbox: os eventos são gravados aqui, na mesma transação que grava o pedido, e só o relay os envia
-- ao RabbitMQ (depois do commit). `seq` dá a ordem de criação; publicada_em NULL = ainda não enviada.
CREATE TABLE outbox (
    seq           BIGINT       NOT NULL AUTO_INCREMENT,
    id            CHAR(36)     NOT NULL,
    tipo          VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    criada_em     DATETIME(6)  NOT NULL,
    publicada_em  DATETIME(6)  NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (seq),
    CONSTRAINT uk_outbox_id UNIQUE (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_outbox_pendentes ON outbox (publicada_em, seq);
