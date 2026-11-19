-- Schema do catálogo. Ids são UUID em texto (CHAR(36)) para ficarem legíveis numa consulta manual.
-- A collation utf8mb4_unicode_ci não diferencia maiúsculas nem acentos: é ela que faz
-- "iris" encontrar "Anel Íris" na busca por texto e ordena nomes em português corretamente.

CREATE TABLE categoria (
    id      CHAR(36)    NOT NULL,
    nome    VARCHAR(60) NOT NULL,
    slug    VARCHAR(80) NOT NULL,
    pai_id  CHAR(36)    NULL,
    CONSTRAINT pk_categoria PRIMARY KEY (id),
    CONSTRAINT uk_categoria_slug UNIQUE (slug),
    CONSTRAINT fk_categoria_pai FOREIGN KEY (pai_id) REFERENCES categoria (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE produto (
    id            CHAR(36)       NOT NULL,
    sku           VARCHAR(32)    NOT NULL,
    nome          VARCHAR(120)   NOT NULL,
    slug          VARCHAR(80)    NOT NULL,
    descricao     VARCHAR(2000)  NOT NULL,
    preco         DECIMAL(12, 2) NOT NULL,
    categoria_id  CHAR(36)       NOT NULL,
    ativo         BOOLEAN        NOT NULL,
    CONSTRAINT pk_produto PRIMARY KEY (id),
    CONSTRAINT uk_produto_sku UNIQUE (sku),
    CONSTRAINT uk_produto_slug UNIQUE (slug),
    CONSTRAINT fk_produto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT ck_produto_preco_positivo CHECK (preco > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Índices para os filtros da vitrine (ativo + categoria, ativo + preço).
CREATE INDEX ix_produto_ativo_categoria ON produto (ativo, categoria_id);
CREATE INDEX ix_produto_ativo_preco ON produto (ativo, preco);

-- Imagens em ordem: posicao 0 é a capa.
CREATE TABLE imagem_produto (
    produto_id         CHAR(36)      NOT NULL,
    posicao            INT           NOT NULL,
    url                VARCHAR(500)  NOT NULL,
    texto_alternativo  VARCHAR(200)  NOT NULL,
    CONSTRAINT pk_imagem_produto PRIMARY KEY (produto_id, posicao),
    CONSTRAINT fk_imagem_produto_produto FOREIGN KEY (produto_id) REFERENCES produto (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
