package com.ruandiego.mini.estoque;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Verificação do provider: o estoque-service cumpre os contratos dos microfrontends em contratos/pacts
 * (ADR-0005)? Sobe o serviço inteiro (HTTP real, MySQL e RabbitMQ em container) e repete cada interação.
 * Hoje só o mfe-admin consome este serviço (indicadores, lista e ajuste do saldo, tudo em /admin).
 */
@Provider("estoque-service")
@PactFolder("../../contratos/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "estoque.outbox.relay-ligado=false"})
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    private static final String PRODUTO = "11111111-1111-4111-8111-111111111111";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void apontarParaOServico(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", porta));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void cumpreOContrato(PactVerificationContext contexto, HttpRequest requisicao) {
        // O mfe-admin chama pelo caminho do gateway (base /api + /estoque): o teste tira o prefixo, como o gateway.
        if ("mfe-admin".equals(contexto.getConsumer().getName())) {
            requisicao.setPath(requisicao.getPath().replaceFirst("^/estoque(?=/)", ""));
        }
        contexto.verifyInteraction();
    }

    // ---- estados (given) ----

    @State("o produto 11111111-1111-4111-8111-111111111111 tem 3 unidades disponíveis no estoque")
    void produtoComTresUnidades() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM item_estoque");
        jdbc.update("INSERT INTO item_estoque (produto_id, disponivel, reservado, atualizado_em) VALUES (?, 3, 0, NOW(6))",
                PRODUTO);
    }

    @State("o produto 55555555-5555-4555-8555-555555555555 não tem estoque cadastrado")
    void produtoSemEstoqueCadastrado() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM item_estoque");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Infra {

        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.4");
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitmq() {
            return new RabbitMQContainer("rabbitmq:4-management");
        }

        /**
         * `token-pact-admin` entra com o papel admin (rotas /admin); qualquer outro token é só cliente e leva 403
         * em /admin. A validação real do JWT (assinatura, emissor) é do Keycloak e tem teste próprio.
         */
        @Bean
        JwtDecoder jwtDoContrato() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").subject("contrato")
                    .claim("realm_access", Map.of("roles", token.contains("admin") ? List.of("admin") : List.of("cliente")))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        }
    }
}
