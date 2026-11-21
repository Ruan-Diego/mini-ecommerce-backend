package com.ruandiego.mini.carrinho;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.memoria.CatalogoProdutosEmMemoria;
import com.ruandiego.mini.carrinho.application.port.in.AdicionarItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AplicarCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import com.ruandiego.mini.kernel.Dinheiro;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Verificação do provider: o carrinho-service cumpre o contrato que o mfe-carrinho gravou em contratos/pacts
 * (ADR-0005)? Sobe o serviço inteiro (HTTP real, Redis e MySQL em container) e repete cada interação.
 * O catálogo (outro serviço) é trocado pelo adapter em memória: aqui só interessa a API do carrinho.
 *
 * Os estados (`given`) montam o carrinho pelos ports de entrada, como o MFE faria.
 */
@Provider("carrinho-service")
@PactFolder("../../contratos/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    private static final SessaoId SESSAO = new SessaoId("sessao-pact");
    private static final ProdutoId PRODUTO = ProdutoId.de("11111111-1111-4111-8111-111111111111");
    /** O `sub` do token de teste: é o cliente logado dos contratos. */
    static final String CLIENTE = "cliente-pact";

    @LocalServerPort
    private int porta;

    @Autowired
    private CatalogoProdutosEmMemoria catalogo;
    @Autowired
    private AdicionarItemUseCase adicionar;
    @Autowired
    private AplicarCupomUseCase aplicarCupom;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void apontarParaOServico(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", porta));
    }

    /**
     * Consumidores que chamam pelo caminho do gateway (base /api + prefixo do serviço): o teste tira o prefixo,
     * como o StripPrefix=2 do gateway. O mfe-carrinho usa o caminho do próprio serviço.
     */
    private static final java.util.Set<String> CONSUMIDORES_PELO_GATEWAY = java.util.Set.of("mfe-checkout", "mfe-conta", "mfe-admin");

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void cumpreOContrato(PactVerificationContext contexto, org.apache.hc.core5.http.HttpRequest requisicao) {
        if (CONSUMIDORES_PELO_GATEWAY.contains(contexto.getConsumer().getName())) {
            requisicao.setPath(requisicao.getPath().replaceFirst("^/carrinho(?=/)", ""));
        }
        contexto.verifyInteraction();
    }

    // ---- estados (given) ----

    @State("o produto 11111111-1111-4111-8111-111111111111 está à venda e o visitante sessao-pact tem o carrinho vazio")
    void produtoAVendaECarrinhoVazio() {
        limpar();
    }

    @State("o visitante sessao-pact tem 2 unidades do produto 11111111-1111-4111-8111-111111111111")
    void duasUnidades() {
        limpar();
        adicionar.adicionar(Identidade.visitante(SESSAO), PRODUTO, 2);
    }

    @State("o visitante sessao-pact tem 2 unidades do produto 11111111-1111-4111-8111-111111111111 e o cupom BEMVINDO")
    void duasUnidadesComCupom() {
        duasUnidades();
        aplicarCupom.aplicar(Identidade.visitante(SESSAO), "BEMVINDO");
    }

    @State("o cliente logado tem 2 unidades do produto 11111111-1111-4111-8111-111111111111 no carrinho")
    void clienteComDuasUnidades() {
        limpar();
        adicionar.adicionar(Identidade.cliente(new com.ruandiego.mini.carrinho.domain.model.ClienteId(CLIENTE)), PRODUTO, 2);
    }

    /** Cada interação começa do zero; o produto do contrato (R$ 100,00) está sempre à venda no catálogo falso. */
    private void limpar() {
        var chaves = redis.keys("carrinho:*");
        if (chaves != null && !chaves.isEmpty()) {
            redis.delete(chaves);
        }
        jdbc.update("DELETE FROM item_carrinho");
        jdbc.update("DELETE FROM carrinho");
        catalogo.limpar();
        catalogo.cadastrar(new CatalogoProdutos.Produto(PRODUTO, "Esfera Chrome", Dinheiro.de("100.00")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Infra {

        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.4");
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitmq() {
            return new RabbitMQContainer("rabbitmq:4-management");
        }

        /**
         * Aceita qualquer Bearer do contrato como o cliente `cliente-pact`. A validação real do JWT (assinatura,
         * emissor) é do Keycloak e já tem teste próprio; aqui interessa só o formato da API.
         */
        @Bean
        org.springframework.security.oauth2.jwt.JwtDecoder jwtDoContrato() {
            return token -> org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                    .header("alg", "none").subject(CLIENTE)
                    .claim("realm_access", java.util.Map.of("roles", java.util.List.of("cliente")))
                    .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300))
                    .build();
        }

        /** O catálogo é outro serviço: no teste de contrato do carrinho, ele vira o adapter em memória. */
        @Bean
        @Primary
        CatalogoProdutosEmMemoria catalogoEmMemoria() {
            return new CatalogoProdutosEmMemoria();
        }
    }
}
