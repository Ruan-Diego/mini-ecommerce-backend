-- Mensagens que este serviço já tratou como consumidor, uma linha por (consumidor, mensagem). A chave primária
-- é o que deixa passar só a primeira entrega de uma mensagem, mesmo quando duas chegam ao mesmo tempo.
-- mensagem_id é o messageId da mensagem, que é o id da linha da outbox de quem publicou.
CREATE TABLE mensagens_processadas (
    consumidor     VARCHAR(60) NOT NULL,
    mensagem_id    CHAR(36)    NOT NULL,
    processada_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_mensagens_processadas PRIMARY KEY (consumidor, mensagem_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
