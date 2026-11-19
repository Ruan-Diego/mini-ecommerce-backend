-- Aviso de mudança de preço no item: o valor que o cliente tinha visto antes de o catálogo mudar o preço.
-- Nulo quando o preço não mudou.
ALTER TABLE item_carrinho
    ADD COLUMN preco_anterior DECIMAL(12, 2) NULL,
    ADD CONSTRAINT ck_item_carrinho_preco_anterior CHECK (preco_anterior IS NULL OR preco_anterior > 0);

-- Último PrecoAlterado já aplicado por produto: é o que torna o consumo idempotente e imune à ordem
-- (evento igual ou mais antigo que o registrado é descartado). Sem chave estrangeira: o produto é do catálogo.
CREATE TABLE preco_processado (
    produto_id   CHAR(36)    NOT NULL,
    ocorrido_em  DATETIME(6) NOT NULL,
    CONSTRAINT pk_preco_processado PRIMARY KEY (produto_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
