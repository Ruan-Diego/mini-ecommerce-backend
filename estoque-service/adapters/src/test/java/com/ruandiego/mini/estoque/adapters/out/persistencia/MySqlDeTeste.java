package com.ruandiego.mini.estoque.adapters.out.persistencia;

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

    /** Apaga reservas, saldos, mensagens tratadas e outbox. O schema fica. */
    static void limpar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM mensagens_processadas");
        jdbc.update("DELETE FROM item_reserva");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM item_estoque");
    }
}
