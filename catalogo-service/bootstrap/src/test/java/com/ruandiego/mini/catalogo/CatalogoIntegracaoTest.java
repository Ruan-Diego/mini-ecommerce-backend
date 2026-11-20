package com.ruandiego.mini.catalogo;

import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ObterProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o catalogo-service inteiro (com o application.yml real) contra um MySQL 8 em container:
 * o Flyway cria o schema, o Hibernate valida as entidades e os use cases chegam pelos ports de entrada.
 */
@SpringBootTest(properties = "catalogo.outbox.relay-ligado=false")
@AutoConfigureMockMvc
class CatalogoIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    /** Ponta a ponta pelo HTTP: POST no admin, GET na vitrine, com o proxy transacional e o MySQL de verdade. */
    @Test
    void cadastraPeloAdminEEncontraNaVitrine() throws Exception {
        Categoria pecas = Categoria.criar("Peças HTTP", null, null);
        categorias.salvar(pecas);

        mvc.perform(post("/admin/produtos").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "PEC-HTTP", "nome": "Peça HTTP", "preco": 12.50, "categoriaId": "%s"}"""
                        .formatted(pecas.id())))
                .andExpect(status().isCreated());

        mvc.perform(get("/produtos").param("categoria", pecas.slug().valor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].slug").value("peca-http"))
                .andExpect(jsonPath("$.itens[0].preco").value(12.50));
    }

    /** O serviço não confia só no gateway: /admin/** sem token é 401 e com token de cliente é 403. */
    @Test
    void adminExigeTokenComOPapelAdmin() throws Exception {
        String corpo = "{\"sku\": \"PEC-SEG\", \"nome\": \"Peça\", \"preco\": 1.00}";

        mvc.perform(post("/admin/produtos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/produtos").with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_cliente")))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"));
    }

    /** Critério de pronto da issue: todos os endpoints aparecem no OpenAPI (base do Swagger UI). */
    @Test
    void documentaOsEndpointsNoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/produtos'].get").exists())
                .andExpect(jsonPath("$.paths['/produtos/{slug}'].get").exists())
                .andExpect(jsonPath("$.paths['/categorias'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/produtos'].post").exists())
                .andExpect(jsonPath("$.paths['/admin/produtos/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/admin/produtos/{id}'].delete").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    /** Publicador de teste que pode ser mandado falhar, para provar que a transação do use case é desfeita. */
    static final AtomicBoolean PUBLICADOR_FALHA = new AtomicBoolean();

    @TestConfiguration(proxyBeanMethods = false)
    static class Infra {

        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.4");
        }

        @Bean
        @ServiceConnection
        org.testcontainers.containers.RabbitMQContainer rabbitmq() {
            return new org.testcontainers.containers.RabbitMQContainer("rabbitmq:4-management");
        }

        /** Fila de teste ligada aos eventos do catálogo, para o teste ver o que o relay publicou. */
        @Bean
        org.springframework.amqp.core.Queue filaDeTeste() {
            // Durável: o RabbitMQ 4 recusa filas transitórias não exclusivas.
            return new org.springframework.amqp.core.Queue(FILA_DE_TESTE, true);
        }

        @Bean
        org.springframework.amqp.core.Binding ligacaoDaFilaDeTeste() {
            return new org.springframework.amqp.core.Binding(FILA_DE_TESTE,
                    org.springframework.amqp.core.Binding.DestinationType.QUEUE, "mini.events", "catalogo.#", null);
        }

        /**
         * Decora a outbox real: depois de gravar o evento (ainda dentro da transação do use case) pode falhar.
         * Assim o teste prova que a transação desfaz o produto E o evento já gravado na outbox.
         */
        @Bean
        @Primary
        PublicadorEventos publicadorQuePodeFalhar(
                com.ruandiego.mini.catalogo.adapters.out.persistencia.OutboxJpaAdapter real) {
            return eventos -> {
                real.publicar(eventos);
                if (PUBLICADOR_FALHA.get()) {
                    throw new IllegalStateException("falha simulada ao publicar");
                }
            };
        }
    }

    private static final String FILA_DE_TESTE = "teste.catalogo-eventos";

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    @Autowired
    private com.ruandiego.mini.catalogo.application.service.RelayDoOutbox relay;

    /**
     * Fecha o ponta a ponta de PrecoAlterado que o roadmap deixava pendente: mudar o preço grava o evento na
     * outbox (JSON plano, o contrato do carrinho), e só quando o relay roda ele chega ao RabbitMQ, com
     * messageId e trace id.
     */
    @Test
    void mudarOPrecoGravaNaOutboxESoSaiParaORabbitMqQuandoORelayRoda() throws Exception {
        jdbc.update("DELETE FROM outbox");
        rabbitAdmin.purgeQueue(FILA_DE_TESTE, false);
        Categoria brincos = Categoria.criar("Brincos", null, null);
        categorias.salvar(brincos);
        ProdutoId id = cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("BRC-LUA"), "Brinco Lua",
                null, "", Dinheiro.de("100.00"), List.of(), brincos.id()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class)).isZero(); // cadastrar não muda preço

        org.slf4j.MDC.put("traceId", "trace-do-teste");
        try {
            atualizar.atualizar(new AtualizarProdutoUseCase.Comando(id, "Brinco Lua", "", Dinheiro.de("80.00"),
                    List.of(), brincos.id()));
        } finally {
            org.slf4j.MDC.remove("traceId");
        }

        assertThat(jdbc.queryForObject("SELECT tipo FROM outbox", String.class)).isEqualTo("catalogo.preco-alterado");
        assertThat(jdbc.queryForObject("SELECT trace_id FROM outbox", String.class)).isEqualTo("trace-do-teste");
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull(); // o relay está desligado: o broker não recebeu nada

        assertThat(relay.publicarPendentes(10)).isEqualTo(1);

        org.springframework.amqp.core.Message mensagem = rabbit.receive(FILA_DE_TESTE, 5_000);
        assertThat(mensagem).isNotNull();
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("catalogo.preco-alterado");
        assertThat(mensagem.getMessageProperties().getMessageId())
                .isEqualTo(jdbc.queryForObject("SELECT id FROM outbox", String.class));
        assertThat(mensagem.getMessageProperties().getHeader("x-trace-id").toString()).isEqualTo("trace-do-teste");
        String corpo = new String(mensagem.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(corpo).contains("\"produtoId\":\"" + id + "\"").contains("\"sku\":\"BRC-LUA\"")
                .contains("\"precoAnterior\":100.00").contains("\"precoNovo\":80.00").contains("\"ocorridoEm\":\"");
    }

    @Autowired
    private CadastrarProdutoUseCase cadastrar;

    @Autowired
    private AtualizarProdutoUseCase atualizar;

    @Autowired
    private ObterProdutoUseCase obter;

    @Autowired
    private CategoriaRepository categorias;

    @Test
    void cadastraEObtemPeloSlug() {
        Categoria esferas = Categoria.criar("Esferas", null, null);
        categorias.salvar(esferas);

        cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("ESF-CHROME"), "Esfera Chrome", null, "",
                Dinheiro.de("289.00"), List.of(), esferas.id()));

        assertThat(obter.obterPorSlug(new Slug("esfera-chrome")).preco()).isEqualTo(Dinheiro.de("289.00"));
    }

    /**
     * Mudar o preço grava o produto e depois publica PrecoAlterado. Se a publicação falha, a gravação
     * também precisa ser desfeita: é o que a transação amarrada no CatalogoConfig garante. Sem ela, o
     * save do repositório já teria sido confirmado sozinho e o preço novo ficaria no banco sem evento.
     */
    @Test
    void falhaAoPublicarDesfazAGravacao() {
        Categoria aneis = Categoria.criar("Anéis", null, null);
        categorias.salvar(aneis);
        ProdutoId id = cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("ANL-IRIS"), "Anel Íris",
                null, "", Dinheiro.de("219.00"), List.of(), aneis.id()));

        PUBLICADOR_FALHA.set(true);
        try {
            assertThatThrownBy(() -> atualizar.atualizar(new AtualizarProdutoUseCase.Comando(id, "Anel Íris", "",
                    Dinheiro.de("199.00"), List.of(), aneis.id())))
                    .hasMessageContaining("falha simulada");
        } finally {
            PUBLICADOR_FALHA.set(false);
        }

        assertThat(obter.obterPorSlug(new Slug("anel-iris")).preco()).isEqualTo(Dinheiro.de("219.00"));
        // O evento já gravado na outbox (antes da falha) também foi desfeito: sem produto alterado, sem evento.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE payload LIKE '%ANL-IRIS%'", Integer.class)).isZero();
    }
}
