package com.ruandiego.mini.catalogo;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
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
        properties = {"catalogo.outbox.relay-ligado=false", "catalogo.imagens.diretorio=build/tmp/pact-imagens"})
@Import(ContratoPactTest.Infra.class)
class ContratoPactTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private ProdutoRepository produtos;
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
    void cumpreOContrato(PactVerificationContext contexto, HttpRequest requisicao) {
        // O mfe-admin chama pelo caminho do gateway (base /api + /catalogo): o teste tira o prefixo, como o gateway.
        if ("mfe-admin".equals(contexto.getConsumer().getName())) {
            requisicao.setPath(requisicao.getPath().replaceFirst("^/catalogo(?=/)", ""));
        }
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

    // ---- estados do admin (mfe-admin): ids fixos, gravados pelo port, para o contrato citá-los no caminho ----

    private static final ProdutoId PRODUTO = ProdutoId.de("11111111-1111-4111-8111-111111111111");
    private static final CategoriaId CATEGORIA = CategoriaId.de("33333333-3333-4333-8333-333333333333");

    @State("existe o produto ativo esfera-chrome com o id 11111111-1111-4111-8111-111111111111")
    void produtoComIdFixo() {
        limpar();
        categorias.salvar(Categoria.reconstituir(CATEGORIA, "Esferas", new Slug("esferas"), null));
        produtos.salvar(Produto.reconstituir(PRODUTO, new Sku("ESF-CHROME-01"), "Esfera Chrome",
                new Slug("esfera-chrome"), "Esfera de aço polido.", Dinheiro.de("289.00"),
                List.of(new Imagem("https://cdn.mini.dev/esfera.webp", "Esfera cromada")), CATEGORIA, true));
    }

    @State("existem o produto ativo esfera-chrome (11111111-1111-4111-8111-111111111111) e o desativado esfera-lime")
    void produtoAtivoEProdutoDesativado() {
        produtoComIdFixo();
        produtos.salvar(Produto.reconstituir(ProdutoId.de("66666666-6666-4666-8666-666666666666"),
                new Sku("ESF-LIME-01"), "Esfera Lime", new Slug("esfera-lime"), "Esfera de vidro lima.",
                Dinheiro.de("199.00"), List.of(new Imagem("https://cdn.mini.dev/lime.webp", "Esfera lima")),
                CATEGORIA, false));
    }

    @State("existe a categoria esferas com o id 33333333-3333-4333-8333-333333333333")
    void categoriaComIdFixo() {
        limpar();
        categorias.salvar(Categoria.reconstituir(CATEGORIA, "Esferas", new Slug("esferas"), null));
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
