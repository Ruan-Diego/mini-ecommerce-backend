package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.application.contrato.CategoriaRepositoryContrato;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** O adapter JPA de categorias contra MySQL 8 de verdade (ver ProdutoRepositoryJpaTest). */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, ProdutoRepositoryJpa.class, CategoriaRepositoryJpa.class})
class CategoriaRepositoryJpaTest extends CategoriaRepositoryContrato {

    @Autowired
    private CategoriaRepositoryJpa categorias;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected CategoriaRepository categorias() {
        return categorias;
    }

    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }
}
