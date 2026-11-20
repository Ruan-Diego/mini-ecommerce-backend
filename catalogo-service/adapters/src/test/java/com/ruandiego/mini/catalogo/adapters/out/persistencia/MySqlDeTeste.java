package com.ruandiego.mini.catalogo.adapters.out.persistencia;

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

    /** Apaga os dados do catálogo, na ordem das chaves estrangeiras. O schema (Flyway) fica. */
    static void limpar(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM imagem_produto");
        jdbc.update("DELETE FROM produto");
        jdbc.update("UPDATE categoria SET pai_id = NULL");
        jdbc.update("DELETE FROM categoria");
    }
}
