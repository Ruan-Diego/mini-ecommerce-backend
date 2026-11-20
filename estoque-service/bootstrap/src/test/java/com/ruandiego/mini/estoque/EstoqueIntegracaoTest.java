package com.ruandiego.mini.estoque;

import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCriado;
import com.ruandiego.mini.estoque.application.service.RelayDoOutbox;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o estoque-service inteiro (com o application.yml real) contra MySQL 8 e RabbitMQ em containers.
 *
 * Prova o critério de pronto da issue: com 20 pedidos disputando 5 unidades, exatamente 5 reservam, o saldo
 * disponível termina em zero e nunca fica negativo. Prova também o caminho completo pelo broker (mensagem do
 * pedido → reserva → evento do estoque) e a DLQ. O relay agendado fica desligado; o teste o aciona à mão.
 */
@SpringBootTest(properties = {
        "estoque.outbox.relay-ligado=false",
        // Retry curto para o teste da DLQ não demorar (em produção: 1 s, 2 s).
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"})
@AutoConfigureMockMvc
@Import(EstoqueIntegracaoTest.Infra.class)
class EstoqueIntegracaoTest {

    private static final String FILA_DE_TESTE = "teste.estoque-eventos";

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
    private ProcessarEventoDePedidoUseCase processar;

    private final ProdutoId produtoA = new ProdutoId(UUID.randomUUID());
    private final ProdutoId produtoB = new ProdutoId(UUID.randomUUID());

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM mensagens_processadas");
        jdbc.update("DELETE FROM item_reserva");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM item_estoque");
        rabbitAdmin.purgeQueue(FILA_DE_TESTE, false);
        rabbitAdmin.purgeQueue("estoque.pedido", false);
        rabbitAdmin.purgeQueue("estoque.pedido.dlq", false);
    }

    private void semear(ProdutoId produto, int disponivel) {
        jdbc.update("INSERT INTO item_estoque (produto_id, disponivel, reservado, atualizado_em) VALUES (?, ?, 0, NOW(6))",
                produto.toString(), disponivel);
    }

    private Map<String, Object> saldo(ProdutoId produto) {
        return jdbc.queryForMap("SELECT disponivel, reservado FROM item_estoque WHERE produto_id = ?", produto.toString());
    }

    private String statusDaReserva(String pedido) {
        List<String> status = jdbc.queryForList("SELECT status FROM reserva WHERE pedido_id = ?", String.class, pedido);
        return status.isEmpty() ? null : status.get(0);
    }

    private int reservasCom(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE status = ?", Integer.class, status);
    }

    private void publicarDoPedido(String chave, String corpoJson, UUID mensagemId) {
        Message mensagem = MessageBuilder.withBody(corpoJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(mensagemId.toString())
                .build();
        rabbit.send("mini.events", chave, mensagem);
    }

    private static String pedidoCriadoJson(String pedido, ProdutoId produto, int quantidade) {
        return "{\"pedidoId\":\"%s\",\"clienteId\":\"ana\",\"total\":99.90,\"itens\":[{\"produtoId\":\"%s\",\"quantidade\":%d}],\"ocorridoEm\":\"2026-11-20T12:00:00Z\"}"
                .formatted(pedido, produto, quantidade);
    }

    private static String pedidoJson(String pedido) {
        return "{\"pedidoId\":\"%s\",\"clienteId\":\"ana\",\"de\":\"X\",\"para\":\"Y\",\"ocorridoEm\":\"2026-11-20T12:00:00Z\"}"
                .formatted(pedido);
    }

    private List<String> tiposNaOutbox() {
        return jdbc.queryForList("SELECT tipo FROM outbox ORDER BY seq", String.class);
    }

    // ---- critério de pronto: o disponível nunca fica negativo, mesmo com pedidos simultâneos ----

    @Test
    void vintePedidosSimultaneosDisputandoCincoUnidadesReservamExatamenteCinco() throws Exception {
        semear(produtoA, 5);
        int pedidos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> tarefas = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                tarefas.add(pool.submit(() -> {
                    largada.await();
                    processar.criado(new PedidoCriado(UUID.randomUUID(), new PedidoId(UUID.randomUUID()),
                            List.of(new ItemReservado(produtoA, 1))));
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> tarefa : tarefas) {
                tarefa.get(); // qualquer exceção (deadlock, violação do CHECK) falha o teste
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(reservasCom("RESERVADA")).isEqualTo(5);
        assertThat(reservasCom("INDISPONIVEL")).isEqualTo(15);
        assertThat(saldo(produtoA)).containsEntry("disponivel", 0).containsEntry("reservado", 5);
        assertThat(tiposNaOutbox().stream().filter("estoque.reservado"::equals)).hasSize(5);
        assertThat(tiposNaOutbox().stream().filter("estoque.indisponivel"::equals)).hasSize(15);
    }

    @Test
    void pedidosComOsMesmosProdutosEmOrdensOpostasNaoTravamEmCiclo() throws Exception {
        semear(produtoA, 40);
        semear(produtoB, 40);
        int pedidos = 40;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> tarefas = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                List<ItemReservado> itens = i % 2 == 0
                        ? List.of(new ItemReservado(produtoA, 1), new ItemReservado(produtoB, 1))
                        : List.of(new ItemReservado(produtoB, 1), new ItemReservado(produtoA, 1));
                tarefas.add(pool.submit(() -> {
                    largada.await();
                    processar.criado(new PedidoCriado(UUID.randomUUID(), new PedidoId(UUID.randomUUID()), itens));
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(reservasCom("RESERVADA")).isEqualTo(40);
        assertThat(saldo(produtoA)).containsEntry("disponivel", 0).containsEntry("reservado", 40);
        assertThat(saldo(produtoB)).containsEntry("disponivel", 0).containsEntry("reservado", 40);
    }

    // ---- caminho completo pelo broker ----

    @Test
    void pedidoCriadoPagoECanceladoPeloBrokerMovemOSaldoEPublicamOsEventos() {
        semear(produtoA, 10);
        String pagoPedido = UUID.randomUUID().toString();
        String canceladoPedido = UUID.randomUUID().toString();

        publicarDoPedido("pedido.criado", pedidoCriadoJson(pagoPedido, produtoA, 3), UUID.randomUUID());
        publicarDoPedido("pedido.criado", pedidoCriadoJson(canceladoPedido, produtoA, 2), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(saldo(produtoA)).containsEntry("disponivel", 5).containsEntry("reservado", 5));

        publicarDoPedido("pedido.pago", pedidoJson(pagoPedido), UUID.randomUUID());
        publicarDoPedido("pedido.cancelado", pedidoJson(canceladoPedido), UUID.randomUUID());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(statusDaReserva(pagoPedido)).isEqualTo("CONFIRMADA");
            assertThat(statusDaReserva(canceladoPedido)).isEqualTo("LIBERADA");
        });
        // Pago: baixa definitiva (sai do reservado). Cancelado: volta ao disponível.
        assertThat(saldo(produtoA)).containsEntry("disponivel", 7).containsEntry("reservado", 0);

        assertThat(relay.publicarPendentes(100)).isEqualTo(4);
        List<String> chaves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Message m = rabbit.receive(FILA_DE_TESTE, 5_000);
            assertThat(m).isNotNull();
            chaves.add(m.getMessageProperties().getReceivedRoutingKey());
        }
        assertThat(chaves).containsExactlyInAnyOrder("estoque.reservado", "estoque.reservado",
                "estoque.baixado", "estoque.liberado");
    }

    @Test
    void estoqueInsuficientePeloBrokerPublicaEstoqueIndisponivelESeparaNada() {
        semear(produtoA, 1);
        String pedido = UUID.randomUUID().toString();

        publicarDoPedido("pedido.criado", pedidoCriadoJson(pedido, produtoA, 5), UUID.randomUUID());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(statusDaReserva(pedido)).isEqualTo("INDISPONIVEL"));
        assertThat(saldo(produtoA)).containsEntry("disponivel", 1).containsEntry("reservado", 0);
        assertThat(tiposNaOutbox()).containsExactly("estoque.indisponivel");
        assertThat(jdbc.queryForObject("SELECT payload FROM outbox", String.class))
                .contains(pedido).contains(produtoA.toString());
    }

    @Test
    void mensagemEntregueDuasVezesReservaUmaSoVez() {
        semear(produtoA, 10);
        String pedido = UUID.randomUUID().toString();
        UUID mensagem = UUID.randomUUID();

        publicarDoPedido("pedido.criado", pedidoCriadoJson(pedido, produtoA, 4), mensagem);
        publicarDoPedido("pedido.criado", pedidoCriadoJson(pedido, produtoA, 4), mensagem);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(statusDaReserva(pedido)).isEqualTo("RESERVADA"));
        // Dá tempo de a segunda entrega ser consumida; o saldo continua descontado uma vez só.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(saldo(produtoA)).containsEntry("disponivel", 6).containsEntry("reservado", 4));
        assertThat(tiposNaOutbox()).containsExactly("estoque.reservado");
    }

    @Test
    void pagoDeUmPedidoSemReservaVaiParaAFilaMortaDepoisDasTentativas() {
        publicarDoPedido("pedido.pago", pedidoJson(UUID.randomUUID().toString()), UUID.randomUUID());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(rabbitAdmin.getQueueInfo("estoque.pedido.dlq").getMessageCount()).isEqualTo(1));
        assertThat(rabbitAdmin.getQueueInfo("estoque.pedido").getMessageCount()).isZero();
    }

    // ---- HTTP ----

    @Test
    void consultaDeSaldoEPublicaMasOAjusteExigeAdmin() throws Exception {
        semear(produtoA, 7);

        mvc.perform(get("/estoque/" + produtoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(7));

        String corpo = "{\"disponivel\":30}";
        mvc.perform(put("/admin/estoque/" + produtoA).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/admin/estoque/" + produtoA).contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .with(jwt().jwt(t -> t.subject("ana"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/admin/estoque/" + produtoA).contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .with(jwt().jwt(t -> t.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(30));
        assertThat(saldo(produtoA)).containsEntry("disponivel", 30);
    }

    /** As consultas do admin exigem o papel admin no próprio serviço, além do gateway. */
    @Test
    void consultasDoAdminExigemOPapelAdmin() throws Exception {
        semear(produtoA, 3);

        for (String rota : java.util.List.of("/admin/estoque?abaixoDe=5", "/admin/indicadores")) {
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
            mvc.perform(get(rota).with(jwt().jwt(t -> t.subject("ana")))).andExpect(status().isForbidden());
        }
        var admin = jwt().jwt(t -> t.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_admin"));
        mvc.perform(get("/admin/estoque").param("abaixoDe", "5").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].produtoId").value(produtoA.toString()))
                .andExpect(jsonPath("$[0].estoqueBaixo").value(true));
        mvc.perform(get("/admin/indicadores").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoqueBaixo").value(1));
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

        /** Fila de teste ligada a todos os eventos do estoque, para o teste ver o que o relay publicou. */
        @Bean
        Queue filaDeTeste() {
            // Durável: o RabbitMQ 4 recusa filas transitórias não exclusivas.
            return new Queue(FILA_DE_TESTE, true);
        }

        @Bean
        Binding ligacaoDaFilaDeTeste() {
            return new Binding(FILA_DE_TESTE, Binding.DestinationType.QUEUE, "mini.events", "estoque.#", null);
        }
    }
}
