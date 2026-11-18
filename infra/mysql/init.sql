-- Um schema e um usuário por serviço. Cada usuário só enxerga o próprio schema.
-- Senhas de desenvolvimento: troque fora do ambiente local.

CREATE DATABASE IF NOT EXISTS catalogo  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS carrinho  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pedido    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pagamento CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS estoque   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'catalogo'@'%'  IDENTIFIED BY 'catalogo';
CREATE USER IF NOT EXISTS 'carrinho'@'%'  IDENTIFIED BY 'carrinho';
CREATE USER IF NOT EXISTS 'pedido'@'%'    IDENTIFIED BY 'pedido';
CREATE USER IF NOT EXISTS 'pagamento'@'%' IDENTIFIED BY 'pagamento';
CREATE USER IF NOT EXISTS 'estoque'@'%'   IDENTIFIED BY 'estoque';

GRANT ALL PRIVILEGES ON catalogo.*  TO 'catalogo'@'%';
GRANT ALL PRIVILEGES ON carrinho.*  TO 'carrinho'@'%';
GRANT ALL PRIVILEGES ON pedido.*    TO 'pedido'@'%';
GRANT ALL PRIVILEGES ON pagamento.* TO 'pagamento'@'%';
GRANT ALL PRIVILEGES ON estoque.*   TO 'estoque'@'%';

FLUSH PRIVILEGES;
