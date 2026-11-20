package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.pedido.application.contrato.PedidoRepositoryContrato;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * O adapter JPA passa nos mesmos cenários do adapter em memória, contra um MySQL de verdade (Flyway cria
 * o schema e o Hibernate valida as entidades contra ele). Sem transação de teste: o @Transactional do
 * @DataJpaTest não vale para métodos herdados do contrato, então cada teste limpa as tabelas.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, PedidoRepositoryJpa.class})
class PedidoRepositoryJpaTest extends PedidoRepositoryContrato {

    @Autowired
    private PedidoRepositoryJpa pedidos;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected PedidoRepository pedidos() {
        return pedidos;
    }
}
