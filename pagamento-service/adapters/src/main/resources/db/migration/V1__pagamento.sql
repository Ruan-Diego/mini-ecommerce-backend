-- Schema do pagamento-service. Ids são UUID em texto (CHAR(36)), como nos outros serviços.
-- pedido_id aponta para o pedido-service, que é outro serviço: por isso não há chave estrangeira.
-- O número do cartão NÃO é guardado: ele vai direto da requisição para o gateway.

CREATE TABLE pagamento (
    id              CHAR(36)       NOT NULL,
    pedido_id       CHAR(36)       NOT NULL,
    cliente_id      VARCHAR(64)    NOT NULL,
    metodo          VARCHAR(10)    NOT NULL,
    valor           DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    transacao_id    VARCHAR(80)    NULL,
    pix_codigo      TEXT           NULL,
    pix_expira_em   DATETIME(6)    NULL,
    motivo_recusa   VARCHAR(200)   NULL,
    criado_em       DATETIME(6)    NOT NULL,
    atualizado_em   DATETIME(6)    NOT NULL,
    CONSTRAINT pk_pagamento PRIMARY KEY (id),
    CONSTRAINT uk_pagamento_transacao UNIQUE (transacao_id),
    CONSTRAINT ck_pagamento_metodo CHECK (metodo IN ('CARTAO', 'PIX')),
    CONSTRAINT ck_pagamento_status CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'EXPIRADO', 'ESTORNADO')),
    CONSTRAINT ck_pagamento_valor_positivo CHECK (valor > 0),
    CONSTRAINT ck_pagamento_pix_completo CHECK (metodo <> 'PIX' OR pix_expira_em IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pagamento_pedido_criado ON pagamento (pedido_id, criado_em);
CREATE INDEX ix_pagamento_pix_pendente ON pagamento (status, metodo, pix_expira_em);

-- Eventos do gateway já tratados: a chave primária (transação, evento) é o que deixa passar só a
-- primeira entrega do webhook, mesmo quando duas chegam ao mesmo tempo.
CREATE TABLE evento_gateway_processado (
    transacao_id  VARCHAR(80) NOT NULL,
    evento        VARCHAR(20) NOT NULL,
    recebido_em   DATETIME(6) NOT NULL,
    CONSTRAINT pk_evento_gateway_processado PRIMARY KEY (transacao_id, evento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Outbox: os eventos são gravados aqui, na mesma transação que grava o pagamento, e só o relay os envia
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
