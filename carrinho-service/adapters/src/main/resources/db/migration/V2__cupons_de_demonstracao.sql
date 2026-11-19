-- Cupons de demonstração, para ver o desconto funcionando no ambiente local.
-- Não há endpoint de administração de cupons no roadmap; novos cupons entram por migração.

INSERT INTO cupom (codigo, tipo, valor, valido_ate, valor_minimo) VALUES
    ('BEMVINDO', 'PERCENTUAL', 10.00, '2030-12-31 23:59:59.000000', 100.00),
    ('FRETE15',  'VALOR_FIXO', 15.00, '2030-12-31 23:59:59.000000', 80.00);
