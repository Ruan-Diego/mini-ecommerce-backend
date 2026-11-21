package com.ruandiego.mini.pagamento;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
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
import java.util.Set;
import java.util.UUID;

/**
 * Verificação do provider: o pagamento-service cumpre os contratos dos microfrontends em contratos/pacts
 * (ADR-0005)? Sobe o serviço inteiro (HTTP real, MySQL e RabbitMQ em container) com o gateway de pagamento
 * fake da configuração padrão (cartão com final par aprova, ímpar recusa) e repete cada interação.
 */
@Provider("pagamento-service")
@PactFolder("../../contratos/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pagamento.outbox.relay-ligado=false",
        "pagamento.pix.expiracao-ligada=false"})
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    /** O `sub` do token de teste: é o cliente logado dos contratos. */
    static final String CLIENTE = "cliente-pact";
    private static final PedidoId PEDIDO = new PedidoId(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    /** Consumidores que chamam pelo caminho do gateway (base /api + /pagamentos): o teste tira o prefixo, como o gateway. */
    private static final Set<String> CONSUMIDORES_PELO_GATEWAY = Set.of("mfe-checkout", "mfe-conta", "mfe-admin");

    @LocalServerPort
    private int porta;

    @Autowired
    private IniciarPagamentoUseCase iniciar;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void apontarParaOServico(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", porta));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void cumpreOContrato(PactVerificationContext contexto, HttpRequest requisicao) {
        if (CONSUMIDORES_PELO_GATEWAY.contains(contexto.getConsumer().getName())) {
            requisicao.setPath(requisicao.getPath().replaceFirst("^/pagamentos(?=/)", ""));
        }
        contexto.verifyInteraction();
    }

    // ---- estados (given) ----

    @State("o pedido 22222222-2222-4222-8222-222222222222 está aberto para pagamento")
    void pedidoAberto() {
        limpar();
    }

    @State("o pedido 22222222-2222-4222-8222-222222222222 tem um pagamento Pix pendente")
    void pixPendente() {
        limpar();
        iniciar.iniciar(new IniciarPagamentoUseCase.Comando(new ClienteId(CLIENTE), PEDIDO, Dinheiro.de("219.90"),
                MetodoPagamento.PIX, null));
    }

    /** Cada interação começa do zero: sem pagamento e sem pedido cancelado. */
    private void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM pedido_cancelado");
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
         * Aceita qualquer Bearer do contrato como o cliente `cliente-pact`. A validação real do JWT (assinatura,
         * emissor) é do Keycloak e tem teste próprio; aqui interessa só o formato da API.
         */
        @Bean
        JwtDecoder jwtDoContrato() {
            return token -> Jwt.withTokenValue(token).header("alg", "none").subject(CLIENTE)
                    .claim("realm_access", Map.of("roles", List.of("cliente")))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        }
    }
}
