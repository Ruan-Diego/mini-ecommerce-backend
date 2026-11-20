package com.ruandiego.mini.pagamento;

import com.ruandiego.mini.kernel.DomainEvent;
import com.ruandiego.mini.pagamento.adapters.out.persistencia.OutboxJpaAdapter;
import com.ruandiego.mini.pagamento.application.port.in.ExpirarPixVencidosUseCase;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pagamento.application.service.RelayDoOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o pagamento-service inteiro (com o application.yml real) contra MySQL 8 e RabbitMQ em containers.
 * Prova pelo HTTP o critério de pronto da issue, com banco e broker de verdade: um webhook repetido (e até
 * vários simultâneos) não gera evento duplicado. Os agendadores ficam desligados; o teste aciona o relay e a
 * expiração à mão, para controlar o momento.
 */
@SpringBootTest(properties = {
        "pagamento.outbox.relay-ligado=false",
        "pagamento.pix.expiracao-ligada=false",
        "pagamento.webhook.segredo=segredo-de-teste"})
@AutoConfigureMockMvc
@Import(PagamentoIntegracaoTest.Infra.class)
class PagamentoIntegracaoTest {

    private static final String FILA_DE_TESTE = "teste.pagamento-eventos";
    private static final String SEGREDO = "segredo-de-teste";
    private static final String CARTAO_APROVADO = "4111111111111112";

    private final String pedidoId = UUID.randomUUID().toString();

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
    private ExpirarPixVencidosUseCase expirar;
    @Autowired
    private Infra.PublicadorDeTeste publicador;

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM evento_gateway_processado");
        jdbc.update("DELETE FROM mensagens_processadas");
        jdbc.update("DELETE FROM pagamento");
        rabbitAdmin.purgeQueue(FILA_DE_TESTE, false);
        rabbitAdmin.purgeQueue("pagamento.pedido", false);
        rabbitAdmin.purgeQueue("pagamento.pedido.dlq", false);
        publicador.falharDepoisDeGravar = false;
        publicador.espiarPorOutraConexao = false;
        publicador.publicadasPorOutraConexao = null;
    }

    private static MockHttpServletRequestBuilder como(String cliente, MockHttpServletRequestBuilder req) {
        return req.with(jwt().jwt(token -> token.subject(cliente)));
    }

    private static MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder req) {
        return req.with(jwt().jwt(token -> token.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_admin")));
    }

    private String cartao(String numero) {
        return "{\"pedidoId\":\"%s\",\"valor\":219.90,\"metodo\":\"CARTAO\",\"numeroCartao\":\"%s\"}".formatted(pedidoId, numero);
    }

    private String pix() {
        return "{\"pedidoId\":\"%s\",\"valor\":219.90,\"metodo\":\"PIX\"}".formatted(pedidoId);
    }

    private MvcResult iniciar(String corpo) throws Exception {
        return mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn();
    }

    private String criarPix() throws Exception {
        iniciar(pix());
        return jdbc.queryForObject("SELECT transacao_id FROM pagamento", String.class);
    }

    private MockHttpServletRequestBuilder webhook(String transacaoId, String evento) {
        return post("/webhooks/gateway").header("X-Webhook-Segredo", SEGREDO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"transacaoId\":\"%s\",\"evento\":\"%s\"}".formatted(transacaoId, evento));
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    private int eventosNaOutbox(String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE tipo = ?", Integer.class, tipo);
    }

    // ---- cartão ----

    @Test
    void cartaoAprovadoGravaNoMySqlEOEventoSoSaiQuandoORelayRoda() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(cartao(CARTAO_APROVADO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APROVADO"));

        assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("APROVADO");
        assertThat(eventosNaOutbox("pagamento.aprovado")).isEqualTo(1);
        // O relay está desligado: o broker não recebeu nada.
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();

        assertThat(relay.publicarPendentes(10)).isEqualTo(1);

        Message mensagem = rabbit.receive(FILA_DE_TESTE, 5_000);
        assertThat(mensagem).isNotNull();
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("pagamento.aprovado");
        assertThat(new String(mensagem.getBody(), StandardCharsets.UTF_8)).contains(pedidoId).contains("\"clienteId\":\"ana\"");
    }

    @Test
    void oNumeroDoCartaoNaoFicaGuardadoEmLugarNenhum() throws Exception {
        iniciar(cartao(CARTAO_APROVADO));
        relay.publicarPendentes(10);

        String tudo = String.join("|", jdbc.queryForList("""
                SELECT CONCAT_WS(',', id, pedido_id, cliente_id, metodo, valor, status, transacao_id, motivo_recusa)
                FROM pagamento UNION ALL SELECT CONCAT_WS(',', tipo, payload) FROM outbox""", String.class));

        assertThat(tudo).isNotBlank().doesNotContain(CARTAO_APROVADO).doesNotContain("4111");
        Message mensagem = rabbit.receive(FILA_DE_TESTE, 5_000);
        assertThat(new String(mensagem.getBody(), StandardCharsets.UTF_8)).doesNotContain("4111");
    }

    @Test
    void cartaoRecusadoGravaPagamentoRecusado() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(cartao("4111111111111113")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        assertThat(eventosNaOutbox("pagamento.recusado")).isEqualTo(1);
    }

    // ---- critério de pronto: webhook repetido não gera evento duplicado ----

    @Test
    void webhookRepetidoNaoGeraEventoDuplicadoNemNoBroker() throws Exception {
        String transacao = criarPix();

        mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PROCESSADO"));
        mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("JA_PROCESSADO"));
        mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("JA_PROCESSADO"));

        assertThat(eventosNaOutbox("pagamento.aprovado")).isEqualTo(1);
        assertThat(contar("evento_gateway_processado")).isEqualTo(1);
        assertThat(relay.publicarPendentes(10)).isEqualTo(1);
        assertThat(rabbit.receive(FILA_DE_TESTE, 5_000)).isNotNull();
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();
    }

    @Test
    void oitoWebhooksSimultaneosGeramUmEventoSo() throws Exception {
        String transacao = criarPix();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> respostas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                respostas.add(pool.submit(() -> mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()));
            }
            long processados = 0;
            for (Future<String> resposta : respostas) {
                if (resposta.get().contains("\"PROCESSADO\"")) {
                    processados++;
                }
            }

            assertThat(processados).isEqualTo(1);
            assertThat(eventosNaOutbox("pagamento.aprovado")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("APROVADO");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void falhaDepoisDeGravarDesfazTudoInclusiveORegistroDoWebhookEOReenvioFunciona() throws Exception {
        String transacao = criarPix();
        publicador.falharDepoisDeGravar = true;

        assertThatThrownBy(() -> mvc.perform(webhook(transacao, "APROVADO"))).hasRootCauseMessage("falha depois de gravar");

        assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("PENDENTE");
        assertThat(contar("evento_gateway_processado")).isZero();
        assertThat(contar("outbox")).isZero();
        assertThat(relay.publicarPendentes(10)).isZero();

        // O gateway reenvia e agora dá certo: o registro do webhook não ficou "queimado" pela falha.
        publicador.falharDepoisDeGravar = false;
        mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PROCESSADO"));
        assertThat(eventosNaOutbox("pagamento.aprovado")).isEqualTo(1);
    }

    @Test
    void relayRodandoEnquantoATransacaoEstaAbertaNaoVeNemPublicaOEvento() throws Exception {
        String transacao = criarPix();
        publicador.espiarPorOutraConexao = true;

        mvc.perform(webhook(transacao, "APROVADO")).andExpect(status().isOk());

        assertThat(publicador.publicadasPorOutraConexao).isZero();
        assertThat(rabbit.receive(FILA_DE_TESTE)).isNull();
        assertThat(relay.publicarPendentes(10)).isEqualTo(1);
        assertThat(rabbit.receive(FILA_DE_TESTE, 5_000)).isNotNull();
    }

    // ---- Pix, estorno e expiração ----

    @Test
    void pixVencidoExpiraEGravaPagamentoRecusadoComMotivo() throws Exception {
        iniciar(pix());
        jdbc.update("UPDATE pagamento SET pix_expira_em = ?", Timestamp.from(Instant.now().minusSeconds(60)));

        assertThat(expirar.expirarVencidos()).isEqualTo(1);
        assertThat(expirar.expirarVencidos()).isZero();

        assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("EXPIRADO");
        assertThat(jdbc.queryForObject("SELECT motivo_recusa FROM pagamento", String.class)).isEqualTo("Pix expirado");
        assertThat(eventosNaOutbox("pagamento.recusado")).isEqualTo(1);
    }

    // ---- compensação da saga: pedido cancelado estorna o pagamento aprovado ----

    private void enviarPedidoCancelado(String messageId) {
        String corpo = "{\"pedidoId\":\"%s\",\"clienteId\":\"ana\",\"de\":\"PAGO\",\"para\":\"CANCELADO\",\"ocorridoEm\":\"2026-11-20T12:00:00Z\"}"
                .formatted(pedidoId);
        rabbit.send("mini.events", "pedido.cancelado", org.springframework.amqp.core.MessageBuilder
                .withBody(corpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(org.springframework.amqp.core.MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId)
                .build());
    }

    @Test
    void pedidoCanceladoEstornaOPagamentoAprovadoEAMensagemRepetidaNaoEstornaDeNovo() throws Exception {
        iniciar(cartao(CARTAO_APROVADO));
        String mensagem = UUID.randomUUID().toString();

        enviarPedidoCancelado(mensagem);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("ESTORNADO"));
        enviarPedidoCancelado(mensagem);
        enviarPedidoCancelado(UUID.randomUUID().toString()); // outra mensagem, mesmo pedido
        Thread.sleep(1_500);

        assertThat(eventosNaOutbox("pagamento.estornado")).isEqualTo(1);
        assertThat(contar("mensagens_processadas")).isEqualTo(2);
        assertThat(rabbit.receive("pagamento.pedido.dlq")).isNull();
    }

    @Test
    void pedidoCanceladoSemPagamentoAprovadoNaoMudaNada() throws Exception {
        iniciar(cartao("4111111111111113")); // recusado

        enviarPedidoCancelado(UUID.randomUUID().toString());
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(contar("mensagens_processadas")).isEqualTo(1));

        assertThat(jdbc.queryForObject("SELECT status FROM pagamento", String.class)).isEqualTo("RECUSADO");
        assertThat(eventosNaOutbox("pagamento.estornado")).isZero();
    }

    @Test
    void soAdminEstornaEOEstornoGravaOEvento() throws Exception {
        iniciar(cartao(CARTAO_APROVADO));
        String id = jdbc.queryForObject("SELECT id FROM pagamento", String.class);

        mvc.perform(como("ana", post("/pagamentos/" + id + "/estorno"))).andExpect(status().isForbidden());
        mvc.perform(post("/pagamentos/" + id + "/estorno")).andExpect(status().isUnauthorized());
        mvc.perform(comoAdmin(post("/pagamentos/" + id + "/estorno"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));

        assertThat(jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class))
                .containsExactly("pagamento.aprovado", "pagamento.estornado");
    }

    // ---- segurança e documentação ----

    @Test
    void semTokenEh401ENoWebhookSemSegredoTambem() throws Exception {
        mvc.perform(post("/pagamentos").contentType(MediaType.APPLICATION_JSON).content(pix()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/pagamentos/pedido/" + pedidoId)).andExpect(status().isUnauthorized());
        // O webhook não usa JWT: sem o segredo, o próprio controller responde 401.
        mvc.perform(post("/webhooks/gateway").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transacaoId\":\"x\",\"evento\":\"APROVADO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentaOsEndpointsNoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/pagamentos'].post").exists())
                .andExpect(jsonPath("$.paths['/pagamentos/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/pagamentos/pedido/{pedidoId}'].get").exists())
                .andExpect(jsonPath("$.paths['/pagamentos/{id}/estorno'].post").exists())
                .andExpect(jsonPath("$.paths['/webhooks/gateway'].post").exists());
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

        /** Fila de teste ligada a todos os eventos do pagamento, para o teste ver o que o relay publicou. */
        @Bean
        Queue filaDeTeste() {
            // Durável: o RabbitMQ 4 recusa filas transitórias não exclusivas.
            return new Queue(FILA_DE_TESTE, true);
        }

        @Bean
        Binding ligacaoDaFilaDeTeste() {
            return new Binding(FILA_DE_TESTE, Binding.DestinationType.QUEUE, "mini.events", "pagamento.#", null);
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
}
