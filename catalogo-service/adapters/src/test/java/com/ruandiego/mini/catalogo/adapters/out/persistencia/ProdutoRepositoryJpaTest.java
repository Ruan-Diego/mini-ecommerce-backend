package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.application.contrato.ProdutoRepositoryContrato;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * O adapter JPA contra um MySQL 8 de verdade, com o schema criado pelo Flyway.
 * ddl-auto=validate faz o Hibernate conferir que as entidades batem com a migração.
 *
 * Sem transação de teste (NOT_SUPPORTED): cada chamada ao adapter abre e confirma a própria transação,
 * como em produção, e toda leitura é um SELECT no banco, não o cache do JPA. Por isso os dados são
 * apagados depois de cada teste.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, ProdutoRepositoryJpa.class, CategoriaRepositoryJpa.class})
class ProdutoRepositoryJpaTest extends ProdutoRepositoryContrato {

    @Autowired
    private ProdutoRepositoryJpa produtos;

    @Autowired
    private CategoriaRepositoryJpa categorias;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected ProdutoRepository produtos() {
        return produtos;
    }

    @Override
    protected CategoriaRepository categorias() {
        return categorias;
    }

    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }
}
