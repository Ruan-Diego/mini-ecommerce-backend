package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.contrato.OutboxContrato;
import com.ruandiego.mini.pagamento.application.port.out.Outbox;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A tabela outbox passa no mesmo contrato da outbox em memória, com o JSON dos eventos gravado de verdade. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, OutboxJpaAdapter.class, JacksonAutoConfiguration.class})
class OutboxJpaAdapterTest extends OutboxContrato {

    @Autowired
    private OutboxJpaAdapter outbox;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected PublicadorEventos publicador() {
        return outbox;
    }

    @Override
    protected Outbox outbox() {
        return outbox;
    }
}
