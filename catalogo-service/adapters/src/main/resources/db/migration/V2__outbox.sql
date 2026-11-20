-- Outbox: os eventos são gravados aqui, na mesma transação que grava o produto, e só o relay os envia
-- ao RabbitMQ (depois do commit). `seq` dá a ordem de criação; publicada_em NULL = ainda não enviada.
-- trace_id é o da requisição que gerou o evento (cabeçalho X-Trace-Id), repassado na mensagem.
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
