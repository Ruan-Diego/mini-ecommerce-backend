package com.ruandiego.mini.estoque.adapters.out.persistencia;

import com.ruandiego.mini.estoque.application.contrato.ReservaRepositoryContrato;
import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** O adapter JPA das reservas passa no mesmo contrato do adapter em memória, contra MySQL de verdade. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, ReservaRepositoryJpa.class})
class ReservaRepositoryJpaTest extends ReservaRepositoryContrato {

    @Autowired
    private ReservaRepositoryJpa reservas;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected ReservaRepository reservas() {
        return reservas;
    }
}
