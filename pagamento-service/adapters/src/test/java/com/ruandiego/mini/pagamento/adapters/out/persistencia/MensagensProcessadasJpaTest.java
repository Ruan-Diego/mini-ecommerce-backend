package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.contrato.MensagensProcessadasContrato;
import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** O INSERT IGNORE passa no contrato e, de verdade, deixa só UMA entre várias entregas simultâneas registrar. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, MensagensProcessadasJpa.class})
class MensagensProcessadasJpaTest extends MensagensProcessadasContrato {

    @Autowired
    private MensagensProcessadasJpa processados;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Override
    protected MensagensProcessadas processadas() {
        return processados;
    }

    @Test
    void entreguesEmParaleloSoUmaRegistra() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            UUID mensagem = UUID.randomUUID();
            for (int i = 0; i < 8; i++) {
                Callable<Boolean> entrega = () -> processados.registrar("consumidor", mensagem);
                resultados.add(pool.submit(entrega));
            }
            long registraram = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    registraram++;
                }
            }

            assertThat(registraram).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensagens_processadas", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }
}
