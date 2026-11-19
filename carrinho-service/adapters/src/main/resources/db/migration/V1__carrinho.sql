-- Schema do carrinho do cliente logado. O carrinho de visitante não passa por aqui: vive no Redis.
-- Ids são UUID em texto (CHAR(36)), como no catálogo. Um cliente tem no máximo um carrinho.
-- O cupom aplicado é guardado por inteiro (uma cópia), e não só o código: o desconto do carrinho
-- não muda se o cupom for editado ou apagado depois.

CREATE TABLE carrinho (
    id                 CHAR(36)       NOT NULL,
    cliente_id         VARCHAR(64)    NOT NULL,
    cupom_codigo       VARCHAR(40)    NULL,
    cupom_tipo         VARCHAR(12)    NULL,
    cupom_valor        DECIMAL(12, 2) NULL,
    cupom_valido_ate   DATETIME(6)    NULL,
    cupom_valor_minimo DECIMAL(12, 2) NULL,
    CONSTRAINT pk_carrinho PRIMARY KEY (id),
    CONSTRAINT uk_carrinho_cliente UNIQUE (cliente_id),
    CONSTRAINT ck_carrinho_cupom_completo CHECK (
        (cupom_codigo IS NULL AND cupom_tipo IS NULL AND cupom_valor IS NULL
            AND cupom_valido_ate IS NULL AND cupom_valor_minimo IS NULL)
        OR (cupom_codigo IS NOT NULL AND cupom_tipo IS NOT NULL AND cupom_valor IS NOT NULL
            AND cupom_valido_ate IS NOT NULL AND cupom_valor_minimo IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Itens em ordem de entrada (posicao). produto_id aponta para o catálogo, que é outro serviço:
-- por isso não há chave estrangeira para ele.
CREATE TABLE item_carrinho (
    carrinho_id     CHAR(36)       NOT NULL,
    posicao         INT            NOT NULL,
    produto_id      CHAR(36)       NOT NULL,
    nome            VARCHAR(120)   NOT NULL,
    preco_unitario  DECIMAL(12, 2) NOT NULL,
    quantidade      INT            NOT NULL,
    CONSTRAINT pk_item_carrinho PRIMARY KEY (carrinho_id, posicao),
    CONSTRAINT uk_item_carrinho_produto UNIQUE (carrinho_id, produto_id),
    CONSTRAINT fk_item_carrinho_carrinho FOREIGN KEY (carrinho_id) REFERENCES carrinho (id) ON DELETE CASCADE,
    CONSTRAINT ck_item_carrinho_preco_positivo CHECK (preco_unitario > 0),
    CONSTRAINT ck_item_carrinho_quantidade CHECK (quantidade BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Cupons cadastrados (o que o cliente digita e o CupomRepository consulta).
CREATE TABLE cupom (
    codigo        VARCHAR(40)    NOT NULL,
    tipo          VARCHAR(12)    NOT NULL,
    valor         DECIMAL(12, 2) NOT NULL,
    valido_ate    DATETIME(6)    NOT NULL,
    valor_minimo  DECIMAL(12, 2) NOT NULL,
    CONSTRAINT pk_cupom PRIMARY KEY (codigo),
    CONSTRAINT ck_cupom_valor_positivo CHECK (valor > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
