package com.ruandiego.mini.estoque.adapters.out.persistencia;

import com.ruandiego.mini.estoque.application.contrato.ItemEstoqueRepositoryContrato;
import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * O adapter JPA passa nos mesmos cenários do adapter em memória, contra um MySQL de verdade (Flyway cria
 * o schema e o Hibernate valida as entidades contra ele). Sem transação de teste: o @Transactional do
 * @DataJpaTest não vale para métodos herdados do contrato, então cada teste limpa as tabelas.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, ItemEstoqueRepositoryJpa.class, ItemEstoqueRepositoryJpaTest.Relogio.class})
class ItemEstoqueRepositoryJpaTest extends ItemEstoqueRepositoryContrato {

    @TestConfiguration(proxyBeanMethods = false)
    static class Relogio {
        @Bean
        Clock relogio() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private ItemEstoqueRepositoryJpa itens;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected ItemEstoqueRepository itens() {
        return itens;
    }
}
