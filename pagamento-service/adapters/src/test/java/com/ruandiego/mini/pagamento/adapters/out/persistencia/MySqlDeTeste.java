package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL num container, na mesma versão do docker-compose. O @ServiceConnection passa URL, usuário e
 * senha do container para o Spring, sem configurar nada à mão. Como os testes importam esta mesma
 * configuração, o Spring reaproveita o contexto e sobe um container só para todos.
 */
@TestConfiguration(proxyBeanMethods = false)
class MySqlDeTeste {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.4");
    }

    /** Apaga pagamentos, eventos processados e outbox. O schema fica. */
    static void limpar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM evento_gateway_processado");
        jdbc.update("DELETE FROM mensagens_processadas");
        jdbc.update("DELETE FROM pedido_cancelado");
        jdbc.update("DELETE FROM pagamento");
    }
}
