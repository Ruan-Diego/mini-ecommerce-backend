package com.ruandiego.mini.pedido;

import com.ruandiego.mini.pedido.adapters.out.persistencia.OutboxJpaAdapter;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.application.service.PagamentoEventosService;
import com.ruandiego.mini.pedido.application.service.RelayDoOutbox;
import com.ruandiego.mini.kernel.DomainEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o pedido-service inteiro (com o application.yml real) contra MySQL 8 e RabbitMQ em containers, com
 * um catálogo falso. Prova pelo HTTP o critério de pronto da issue: nenhum evento chega ao broker antes do
 * commit do pedido. O relay agendado fica desligado e o teste o aciona à mão, para controlar o momento.
 */
@SpringBootTest(properties = {
        "pedido.outbox.relay-ligado=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"})
@AutoConfigureMockMvc
@Import(PedidoIntegracaoTest.Infra.class)
class PedidoIntegracaoTest {

    private static final String PRODUTO_ID = UUID.randomUUID().toString();
    private static final String FILA_DE_TESTE = "teste.pedido-eventos";
    private static final HttpServer CATALOGO_FALSO = catalogoFalso();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("pedido.catalogo.url", () -> "http://localhost:" + CATALOGO_FALSO.getAddress().getPort());
    }

    @AfterAll
    static void pararCatalogoFalso() {
        CATALOGO_FALSO.stop(0);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private RelayDoOutbox relay;
    @Autowired
    private Infra.PublicadorDeTeste publicador;
    @Autowired
    private Infra.ContadorDeTentativas tentativas;
    @Autowired
    private RabbitMQContainer rabbitmq;

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM item_pedido");
        jdbc.update("DELETE FROM pedido");
        rabbitAdmin.purgeQueue(FILA_DE_TESTE, false);
        rabbitAdmin.purgeQueue("pedido.pagamento", false);
        rabbitAdmin.purgeQueue("pedido.pagamento.dlq", false);
        rabbitAdmin.purgeQueue("pedido.estoque", false);
        rabbitAdmin.purgeQueue("pedido.estoque.dlq", false);
        jdbc.update("DELETE FROM mensagens_processadas");
        tentativas.zerar();
        publicador.falharDepoisDeGravar = false;
        publicador.espiarPorOutraConexao = false;
        publicador.publicadasPorOutraConexao = null;
    }

    private static MockHttpServletRequestBuilder como(String cliente, MockHttpServletRequestBuilder req) {
        return req.with(jwt().jwt(token -> token.subject(cliente)));
    }

    private static String corpo(String freteCodigo) {
        return """
                {"itens":[{"produtoId":"%s","quantidade":2,"precoVisto":100.00}],
                 "endereco":{"destinatario":"Ana Souza","cep":"01310-100","logradouro":"Av. Paulista","numero":"1000",
                             "bairro":"Bela Vista","cidade":"São Paulo","uf":"SP"},
                 "freteCodigo":"%s"}""".formatted(PRODUTO_ID, freteCodigo);
    }

    private MvcResult criar(String cliente) throws Exception {
        return mvc.perform(como(cliente, post("/pedidos")).contentType(MediaType.APPLICATION_JSON).content(corpo("padrao")))
                .andExpect(status().isCreated()).andReturn();
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    // ---- ponta a ponta ----

    @Test
    void criaOPedidoNoMySqlEGravaOEventoNaOutboxSemPublicarNoBroker() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON).content(corpo("padrao")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.itens[0].nome").value("Esfera Chrome"))
                // 200,00 de itens + 19,90 do frete padrão (abaixo de R$ 299,00 não é grátis).
                .andExpect(jsonPath("$.total").value(219.90));

        assertThat(contar("pedido")).isEqualTo(1);
        assertThat(contar("item_pedido")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE tipo = 'pedido.criado' AND publicada_em IS NULL",
                Integer.class)).isEqualTo(1);
        // O relay está desligado: o broker não recebeu nada.
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();
    }

    @Test
    void relayPublicaNoRabbitMqEDaBaixaNaOutbox() throws Exception {
        MvcResult resultado = criar("ana");
        String id = jdbc.queryForObject("SELECT id FROM pedido", String.class);

        assertThat(relay.publicarPendentes(10)).isEqualTo(1);

        Message mensagem = rabbit.receive(FILA_DE_TESTE, 5_000);
        assertThat(mensagem).isNotNull();
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("pedido.criado");
        assertThat(new String(mensagem.getBody(), StandardCharsets.UTF_8)).contains(id).contains("\"clienteId\":\"ana\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE publicada_em IS NULL", Integer.class)).isZero();
        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(resultado.getResponse().getContentAsString()).contains(id);
    }

    // ---- critério de pronto: nada é publicado antes do commit ----

    @Test
    void falhaDepoisDeGravarDesfazOPedidoEAOutboxENadaChegaAoBroker() {
        publicador.falharDepoisDeGravar = true;

        assertThatThrownBy(() -> mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("padrao")))).hasRootCauseMessage("falha depois de gravar");

        assertThat(contar("pedido")).isZero();
        assertThat(contar("item_pedido")).isZero();
        assertThat(contar("outbox")).isZero();
        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();
    }

    @Test
    void relayRodandoEnquantoATransacaoEstaAbertaNaoVeNemPublicaOEvento() throws Exception {
        publicador.espiarPorOutraConexao = true;

        criar("ana");

        // Durante a transação (evento já gravado, ainda sem commit), outra conexão rodou o relay: 0 publicadas.
        assertThat(publicador.publicadasPorOutraConexao).isZero();
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();
        // Depois do commit o evento aparece e sai.
        assertThat(relay.publicarPendentes(10)).isEqualTo(1);
        assertThat(rabbit.receive(FILA_DE_TESTE, 5_000)).isNotNull();
    }

    // ---- cancelar e consultas ----

    @Test
    void cancelarGravaOEventoEPedidoDeOutroClienteNaoEhVisivel() throws Exception {
        criar("ana");
        String id = jdbc.queryForObject("SELECT id FROM pedido", String.class);

        mvc.perform(como("beto", get("/pedidos/" + id))).andExpect(status().isNotFound());
        mvc.perform(como("beto", post("/pedidos/" + id + "/cancelar"))).andExpect(status().isNotFound());
        mvc.perform(como("ana", post("/pedidos/" + id + "/cancelar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        mvc.perform(como("ana", post("/pedidos/" + id + "/cancelar"))).andExpect(status().isUnprocessableEntity());

        assertThat(jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class))
                .containsExactly("pedido.criado", "pedido.cancelado");
        mvc.perform(como("ana", get("/pedidos/meus"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELADO"));
    }

    // ---- segurança e documentação ----

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/pedidos/meus")).andExpect(status().isUnauthorized());
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(corpo("padrao")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/fretes/cotacao").param("cep", "01310100").param("subtotal", "100"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentaOsEndpointsNoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/pedidos'].post").exists())
                .andExpect(jsonPath("$.paths['/pedidos/meus'].get").exists())
                .andExpect(jsonPath("$.paths['/pedidos/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/pedidos/{id}/cancelar'].post").exists())
                .andExpect(jsonPath("$.paths['/fretes/cotacao'].get").exists());
    }

    // ---- consumo de eventos do pagamento: idempotência, retry e DLQ ----

    private void enviarEventoDePagamento(String chave, String messageId, String clienteId, String valor, String traceId) {
        String corpo = "{\"pagamentoId\":\"%s\",\"pedidoId\":\"%s\",\"clienteId\":\"%s\",\"valor\":%s,\"transacaoId\":\"tx-1\"}"
                .formatted(UUID.randomUUID(), pedidoDoBanco(), clienteId, valor);
        var construtor = MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId);
        if (traceId != null) {
            construtor.setHeader("x-trace-id", traceId);
        }
        rabbit.send("mini.events", chave, construtor.build());
    }

    private String pedidoDoBanco() {
        return jdbc.queryForObject("SELECT id FROM pedido", String.class);
    }

    private String statusDoPedido() {
        return jdbc.queryForObject("SELECT status FROM pedido", String.class);
    }

    @Test
    void pagamentoAprovadoMarcaOPedidoComoPagoEAMesmaMensagemEntregueDeNovoNaoFazNada() throws Exception {
        criar("ana");
        String mensagem = UUID.randomUUID().toString();

        enviarEventoDePagamento("pagamento.aprovado", mensagem, "ana", "219.90", null);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("PAGO"));

        // O RabbitMQ entrega "pelo menos uma vez": a mesma mensagem de novo (mesmo messageId) não tem efeito.
        enviarEventoDePagamento("pagamento.aprovado", mensagem, "ana", "219.90", null);
        enviarEventoDePagamento("pagamento.aprovado", mensagem, "ana", "219.90", null);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(tentativas.total()).isEqualTo(3));

        assertThat(statusDoPedido()).isEqualTo("PAGO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE tipo = 'pedido.pago'", Integer.class)).isEqualTo(1);
        assertThat(contar("mensagens_processadas")).isEqualTo(1);
        assertThat(rabbit.receive("pedido.pagamento.dlq")).isNull();
    }

    @Test
    void pagamentoEstornadoCancelaOPedidoPago() throws Exception {
        criar("ana");
        enviarEventoDePagamento("pagamento.aprovado", UUID.randomUUID().toString(), "ana", "219.90", null);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("PAGO"));

        enviarEventoDePagamento("pagamento.estornado", UUID.randomUUID().toString(), "ana", "219.90", null);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("CANCELADO"));
        assertThat(jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class))
                .containsExactly("pedido.criado", "pedido.pago", "pedido.cancelado");
    }

    // ---- compensação da saga: estoque indisponível cancela o pedido ----

    private void enviarEstoqueIndisponivel(String messageId) {
        String corpo = "{\"pedidoId\":\"%s\",\"produtosSemEstoque\":[\"%s\"],\"ocorridoEm\":\"2026-11-20T12:00:00Z\"}"
                .formatted(pedidoDoBanco(), UUID.randomUUID());
        rabbit.send("mini.events", "estoque.indisponivel", MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId)
                .build());
    }

    @Test
    void estoqueIndisponivelCancelaOPedidoQueAguardaPagamentoEPublicaPedidoCancelado() throws Exception {
        criar("ana");

        enviarEstoqueIndisponivel(UUID.randomUUID().toString());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("CANCELADO"));
        assertThat(jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class))
                .containsExactly("pedido.criado", "pedido.cancelado");
    }

    @Test
    void estoqueIndisponivelCancelaTambemOPedidoQueJaFoiPagoEMensagemRepetidaNaoFazNada() throws Exception {
        criar("ana");
        // O cliente pagou antes de o estoque responder: o cancelamento é o que faz o pagamento estornar.
        enviarEventoDePagamento("pagamento.aprovado", UUID.randomUUID().toString(), "ana", "219.90", null);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("PAGO"));
        String mensagem = UUID.randomUUID().toString();

        enviarEstoqueIndisponivel(mensagem);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(statusDoPedido()).isEqualTo("CANCELADO"));
        enviarEstoqueIndisponivel(mensagem);
        enviarEstoqueIndisponivel(mensagem);
        Thread.sleep(1_500);

        assertThat(jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class))
                .containsExactly("pedido.criado", "pedido.pago", "pedido.cancelado");
        assertThat(rabbit.receive("pedido.estoque.dlq")).isNull();
    }

    @Test
    void mensagemQueFalhaTresVezesVaiParaADlqEApareceNoPainelDoRabbitMq() throws Exception {
        criar("ana");
        // O valor (R$ 1,00) não confere com o total do pedido: o use case recusa em toda tentativa.
        enviarEventoDePagamento("pagamento.aprovado", UUID.randomUUID().toString(), "ana", "1.00", null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(tentativas.total()).isEqualTo(3));
        Message morta = rabbit.receive("pedido.pagamento.dlq", 10_000);
        assertThat(morta).isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).contains(pedidoDoBanco());
        // O broker registra de onde a mensagem morreu (cabeçalho x-death).
        assertThat(morta.getMessageProperties().getHeaders()).containsKey("x-death");

        // Nada mudou no pedido, e depois das 3 tentativas ninguém tenta mais.
        assertThat(statusDoPedido()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(contar("mensagens_processadas")).isZero();
        Thread.sleep(1_500);
        assertThat(tentativas.total()).isEqualTo(3);
    }

    @Test
    void aDlqApareceNaApiDeGerenciamentoDoRabbitMq() throws Exception {
        criar("ana");
        enviarEventoDePagamento("pagamento.aprovado", UUID.randomUUID().toString(), "ana", "1.00", null);

        // É a mesma API que alimenta o painel (http://localhost:15672). As estatísticas dela atualizam a cada ~5 s.
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(rabbitmq.getHttpUrl() + "/api/queues/%2F/pedido.pagamento.dlq"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("guest:guest".getBytes(StandardCharsets.UTF_8)))
                .build();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String corpo = http.send(requisicao, HttpResponse.BodyHandlers.ofString()).body();
            assertThat(corpo).contains("\"name\":\"pedido.pagamento.dlq\"").containsPattern("\"messages\":1[,}]");
        });
    }

    // ---- trace id ----

    @Test
    void oTraceIdDaRequisicaoChegaNaOutboxENaMensagemPublicada() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).header("X-Trace-Id", "trace-abc.123")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("padrao")))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Trace-Id", "trace-abc.123"));

        assertThat(jdbc.queryForObject("SELECT trace_id FROM outbox", String.class)).isEqualTo("trace-abc.123");
        relay.publicarPendentes(10);
        Message mensagem = rabbit.receive(FILA_DE_TESTE, 5_000);
        assertThat(mensagem).isNotNull();
        assertThat(mensagem.getMessageProperties().getHeader("x-trace-id").toString()).isEqualTo("trace-abc.123");
        assertThat(mensagem.getMessageProperties().getMessageId())
                .isEqualTo(jdbc.queryForObject("SELECT id FROM outbox", String.class));
    }

    @Test
    void semCabecalhoOServicoGeraUmTraceIdENaoGravaNulo() throws Exception {
        criar("ana");

        assertThat(jdbc.queryForObject("SELECT trace_id FROM outbox", String.class)).matches("[0-9a-f]{32}");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Infra {

        /** Conta quantas vezes o listener chamou o use case (cada tentativa do retry conta uma). */
        static class ContadorDeTentativas implements ProcessarEventoDePagamentoUseCase {
            private final ProcessarEventoDePagamentoUseCase real;
            private final AtomicInteger chamadas = new AtomicInteger();

            ContadorDeTentativas(ProcessarEventoDePagamentoUseCase real) {
                this.real = real;
            }

            int total() {
                return chamadas.get();
            }

            void zerar() {
                chamadas.set(0);
            }

            @Override
            public void aprovado(PagamentoAprovado evento) {
                chamadas.incrementAndGet();
                real.aprovado(evento);
            }

            @Override
            public void estornado(PagamentoEstornado evento) {
                chamadas.incrementAndGet();
                real.estornado(evento);
            }
        }

        @Bean
        @Primary
        ContadorDeTentativas contadorDeTentativas(PagamentoEventosService real) {
            return new ContadorDeTentativas(real);
        }


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

        /** Fila de teste ligada a todos os eventos do pedido, para o teste ver o que o relay publicou. */
        @Bean
        Queue filaDeTeste() {
            // Durável: o RabbitMQ 4 recusa filas transitórias não exclusivas.
            return new Queue(FILA_DE_TESTE, true);
        }

        @Bean
        Binding ligacaoDaFilaDeTeste() {
            return new Binding(FILA_DE_TESTE, Binding.DestinationType.QUEUE, "mini.events", "pedido.#", null);
        }

        /**
         * Decora a outbox real para os cenários de teste: depois de gravar o evento (ainda dentro da
         * transação do use case) ele pode falhar, ou rodar o relay a partir de OUTRA thread (outra conexão).
         */
        @Bean
        @Primary
        PublicadorDeTeste publicadorDeTeste(OutboxJpaAdapter real, ObjectProvider<RelayDoOutbox> relay) {
            return new PublicadorDeTeste(real, relay);
        }

        static class PublicadorDeTeste implements PublicadorEventos {
            private final PublicadorEventos real;
            private final ObjectProvider<RelayDoOutbox> relay;
            volatile boolean falharDepoisDeGravar;
            volatile boolean espiarPorOutraConexao;
            volatile Integer publicadasPorOutraConexao;

            PublicadorDeTeste(PublicadorEventos real, ObjectProvider<RelayDoOutbox> relay) {
                this.real = real;
                this.relay = relay;
            }

            @Override
            public void publicar(List<? extends DomainEvent> eventos) {
                real.publicar(eventos);
                // Força o INSERT agora: sem isso o Hibernate poderia adiar a escrita até o commit.
                if (espiarPorOutraConexao) {
                    ExecutorService outraThread = Executors.newSingleThreadExecutor();
                    try {
                        publicadasPorOutraConexao = outraThread.submit(() -> relay.getObject().publicarPendentes(10)).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        outraThread.shutdown();
                    }
                }
                if (falharDepoisDeGravar) {
                    throw new IllegalStateException("falha depois de gravar");
                }
            }
        }
    }

    /** Catálogo falso: responde o produto PRODUTO_ID (R$ 100,00) e 404 para os outros. */
    private static HttpServer catalogoFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/produtos/por-id/", troca -> {
                String id = troca.getRequestURI().getPath().substring(troca.getRequestURI().getPath().lastIndexOf('/') + 1);
                boolean existe = id.equals(PRODUTO_ID);
                String corpo = existe
                        ? "{\"id\":\"" + id + "\",\"nome\":\"Esfera Chrome\",\"preco\":100.00,\"ativo\":true}"
                        : "{\"title\":\"Não encontrado\"}";
                byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(existe ? 200 : 404, bytes.length);
                troca.getResponseBody().write(bytes);
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
