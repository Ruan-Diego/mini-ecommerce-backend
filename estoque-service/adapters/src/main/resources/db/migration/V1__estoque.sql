-- Schema do estoque-service. Ids são UUID em texto (CHAR(36)), como nos outros serviços.
-- produto_id e pedido_id apontam para o catálogo e o pedido-service, que são outros serviços: sem chave estrangeira.

-- Saldo por produto. As duas CHECK são a última barreira contra saldo negativo: o domínio e a trava de linha
-- (SELECT ... FOR UPDATE) já impedem, e o banco recusa a gravação se mesmo assim algo tentar.
CREATE TABLE item_estoque (
    produto_id     CHAR(36)    NOT NULL,
    disponivel     INT         NOT NULL,
    reservado      INT         NOT NULL,
    atualizado_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_item_estoque PRIMARY KEY (produto_id),
    CONSTRAINT ck_item_estoque_disponivel CHECK (disponivel >= 0),
    CONSTRAINT ck_item_estoque_reservado CHECK (reservado >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Uma reserva por pedido: a chave primária garante que o mesmo pedido não é reservado duas vezes.
CREATE TABLE reserva (
    pedido_id      CHAR(36)    NOT NULL,
    status         VARCHAR(12) NOT NULL,
    criada_em      DATETIME(6) NOT NULL,
    expira_em      DATETIME(6) NOT NULL,
    atualizada_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_reserva PRIMARY KEY (pedido_id),
    CONSTRAINT ck_reserva_status CHECK (status IN ('RESERVADA', 'CONFIRMADA', 'LIBERADA', 'INDISPONIVEL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_reserva_status_expira ON reserva (status, expira_em);

CREATE TABLE item_reserva (
    pedido_id   CHAR(36) NOT NULL,
    produto_id  CHAR(36) NOT NULL,
    quantidade  INT      NOT NULL,
    CONSTRAINT pk_item_reserva PRIMARY KEY (pedido_id, produto_id),
    CONSTRAINT fk_item_reserva_reserva FOREIGN KEY (pedido_id) REFERENCES reserva (pedido_id),
    CONSTRAINT ck_item_reserva_quantidade CHECK (quantidade > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Mensagens que este serviço já tratou como consumidor, uma linha por (consumidor, mensagem). A chave primária
-- é o que deixa passar só a primeira entrega de uma mensagem, mesmo quando duas chegam ao mesmo tempo.
-- mensagem_id é o messageId da mensagem, que é o id da linha da outbox de quem publicou.
CREATE TABLE mensagens_processadas (
    consumidor     VARCHAR(60) NOT NULL,
    mensagem_id    CHAR(36)    NOT NULL,
    processada_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_mensagens_processadas PRIMARY KEY (consumidor, mensagem_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Outbox: os eventos são gravados aqui, na mesma transação que muda o saldo, e só o relay os envia ao
-- RabbitMQ (depois do commit). `seq` dá a ordem de criação; publicada_em NULL = ainda não enviada.
-- trace_id é o da requisição ou mensagem que gerou o evento; o relay o repassa no cabeçalho x-trace-id.
CREATE TABLE outbox (
    seq           BIGINT       NOT NULL AUTO_INCREMENT,
    id            CHAR(36)     NOT NULL,
    tipo          VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    criada_em     DATETIME(6)  NOT NULL,
    trace_id      VARCHAR(64)  NULL,
    publicada_em  DATETIME(6)  NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (seq),
    CONSTRAINT uk_outbox_id UNIQUE (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_outbox_pendentes ON outbox (publicada_em, seq);
