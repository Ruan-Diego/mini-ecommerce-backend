package com.ruandiego.mini.pedido;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.memoria.CatalogoConsultaEmMemoria;
import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
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
import org.springframework.context.annotation.Primary;
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
 * Verificação do provider: o pedido-service cumpre os contratos dos microfrontends em contratos/pacts
 * (ADR-0005)? Sobe o serviço inteiro (HTTP real, MySQL e RabbitMQ em container) e repete cada interação.
 * O catálogo (outro serviço) vira o adapter em memória; o frete é o fixo (padrão da configuração).
 */
@Provider("pedido-service")
@PactFolder("../../contratos/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pedido.outbox.relay-ligado=false",
        "pedido.expiracao.ligada=false"})
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    /** O `sub` do token de teste: é o cliente logado dos contratos. */
    static final String CLIENTE = "cliente-pact";
    private static final ProdutoId PRODUTO = new ProdutoId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final PedidoId PEDIDO = new PedidoId(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    /** Consumidores que chamam pelo caminho do gateway (base /api + /pedidos): o teste tira o prefixo, como o gateway. */
    private static final Set<String> CONSUMIDORES_PELO_GATEWAY = Set.of("mfe-checkout", "mfe-conta", "mfe-admin");

    @LocalServerPort
    private int porta;

    @Autowired
    private CatalogoConsultaEmMemoria catalogo;
    @Autowired
    private PedidoRepository pedidos;
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
            requisicao.setPath(requisicao.getPath().replaceFirst("^/pedidos(?=/)", ""));
        }
        contexto.verifyInteraction();
    }

    // ---- estados (given) ----

    @State("o produto 11111111-1111-4111-8111-111111111111 custa 100,00 no catálogo")
    void produtoNoCatalogo() {
        limpar();
    }

    @State("existe o pedido 22222222-2222-4222-8222-222222222222 do cliente logado")
    void pedidoDoCliente() {
        limpar();
        Instant criadoEm = Instant.parse("2026-11-20T12:00:00Z");
        pedidos.salvar(Pedido.reconstituir(PEDIDO, new ClienteId(CLIENTE),
                List.of(new ItemPedido(PRODUTO, "Esfera Chrome", Dinheiro.de("100.00"), 2)),
                new EnderecoEntrega("Ana Souza", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7), StatusPedido.PAGO, criadoEm, criadoEm));
    }

    /** Cada interação começa do zero; o produto do contrato (R$ 100,00) está sempre no catálogo falso. */
    private void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM item_pedido");
        jdbc.update("DELETE FROM pedido");
        catalogo.limpar();
        catalogo.cadastrar(new CatalogoConsulta.Produto(PRODUTO, "Esfera Chrome", Dinheiro.de("100.00")));
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

        /** O catálogo é outro serviço: no teste de contrato, ele vira o adapter em memória. */
        @Bean
        @Primary
        CatalogoConsultaEmMemoria catalogoEmMemoria() {
            return new CatalogoConsultaEmMemoria();
        }
    }
}
