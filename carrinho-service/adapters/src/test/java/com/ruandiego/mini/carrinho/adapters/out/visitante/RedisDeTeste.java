package com.ruandiego.mini.carrinho.adapters.out.visitante;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/** Redis num container, na mesma versão do docker-compose. O @ServiceConnection configura host e porta. */
@TestConfiguration(proxyBeanMethods = false)
class RedisDeTeste {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
