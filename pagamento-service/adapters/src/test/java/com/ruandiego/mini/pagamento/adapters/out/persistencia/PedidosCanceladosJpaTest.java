package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.contrato.PedidosCanceladosContrato;
import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** O registro de pedidos cancelados passa no mesmo contrato do adapter em memória, contra MySQL de verdade. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, PedidosCanceladosJpa.class})
class PedidosCanceladosJpaTest extends PedidosCanceladosContrato {

    @Autowired
    private PedidosCanceladosJpa cancelados;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected PedidosCancelados cancelados() {
        return cancelados;
    }
}
