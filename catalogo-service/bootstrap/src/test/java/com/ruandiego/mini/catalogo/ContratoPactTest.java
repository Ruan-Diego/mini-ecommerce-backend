package com.ruandiego.mini.catalogo;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.List;

/**
 * Verificação do provider: o catalogo-service cumpre os contratos que os microfrontends gravaram em
 * contratos/pacts (ADR-0005)? Sobe o serviço inteiro (HTTP real, MySQL em container) e repete cada interação
 * do contrato; uma mudança incompatível na API (campo renomeado, tipo trocado, status diferente) quebra aqui,
 * no ./gradlew build e no CI.
 *
 * Cada `given` do contrato vira um @State, que deixa o banco no estado esperado pelos ports de entrada e
 * saída (como faria o admin), e não por SQL solto.
 */
@Provider("catalogo-service")
@PactFolder("../../contratos/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "catalogo.outbox.relay-ligado=false")
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private CadastrarProdutoUseCase cadastrar;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void apontarParaOServico(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", porta));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void cumpreOContrato(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    // ---- estados (given) ----

    @State("existem produtos ativos na categoria esferas")
    void produtosNaCategoriaEsferas() {
        esferaChrome();
    }

    @State("existe o produto ativo esfera-chrome")
    void produtoEsferaChrome() {
        esferaChrome();
    }

    @State("existe a categoria esferas")
    void categoriaEsferas() {
        limpar();
        categorias.salvar(Categoria.criar("Esferas", new Slug("esferas"), null));
    }

    @State("não existe produto com o slug nao-existe")
    void semProduto() {
        limpar();
    }

    private void esferaChrome() {
        limpar();
        Categoria esferas = Categoria.criar("Esferas", new Slug("esferas"), null);
        categorias.salvar(esferas);
        cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("ESF-CHROME-01"), "Esfera Chrome",
                new Slug("esfera-chrome"), "Esfera de aço polido.", Dinheiro.de("289.00"),
                List.of(new Imagem("https://cdn.mini.dev/esfera.webp", "Esfera cromada")), esferas.id()));
    }

    /** Cada interação começa do zero: o estado de uma não vaza para a outra. */
    private void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM imagem_produto");
        jdbc.update("DELETE FROM produto");
        jdbc.update("DELETE FROM categoria");
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
    }
}
