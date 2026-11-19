package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.contrato.PrecosJaProcessadosContrato;
import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** O adapter JPA do marco de eventos de preço contra MySQL 8 de verdade, com o mesmo contrato do em memória. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, PrecosJaProcessadosJpa.class})
class PrecosJaProcessadosJpaTest extends PrecosJaProcessadosContrato {

    @Autowired
    private PrecosJaProcessadosJpa processados;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected PrecosJaProcessados processados() {
        return processados;
    }

    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }
}
