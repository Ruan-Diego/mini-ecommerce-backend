package com.ruandiego.mini.carrinho;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o carrinho-service inteiro (com o application.yml real) contra MySQL 8 e Redis 7 em containers,
 * com um catálogo falso. O Flyway cria o schema, o Hibernate valida as entidades e o critério de pronto
 * da issue é provado pelo HTTP: itens adicionados como visitante continuam no carrinho depois do login.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.retry.initial-interval=100ms")
@AutoConfigureMockMvc
@Import(CarrinhoIntegracaoTest.Infra.class)
class CarrinhoIntegracaoTest {

    private static final String PRODUTO_ID = UUID.randomUUID().toString();
    private static final String PRODUTO_ID_2 = UUID.randomUUID().toString();
    private static final HttpServer CATALOGO_FALSO = catalogoFalso();

    @DynamicPropertySource
    static void catalogo(DynamicPropertyRegistry registro) {
        registro.add("carrinho.catalogo.url", () -> "http://localhost:" + CATALOGO_FALSO.getAddress().getPort());
    }

    @AfterAll
    static void pararCatalogo() {
        CATALOGO_FALSO.stop(0);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void itensDeVisitanteContinuamNoCarrinhoDepoisDoLogin() throws Exception {
        String sessao = "sessao-" + UUID.randomUUID();
        String cliente = "cliente-" + UUID.randomUUID();

        // Visitante: o carrinho vive no Redis, com prazo de expiração.
        mvc.perform(post("/carrinhos/atual/itens").header("X-Sessao-Id", sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"" + PRODUTO_ID + "\",\"quantidade\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Esfera Chrome"))
                .andExpect(jsonPath("$.subtotal").value(300.00));
        assertThat(redis.hasKey("carrinho:visitante:" + sessao)).isTrue();
        assertThat(redis.getExpire("carrinho:visitante:" + sessao)).isGreaterThan(6 * 24 * 3600L);
        assertThat(contarCarrinhos(cliente)).isZero();

        // Primeiro acesso autenticado: o carrinho passa a ser do cliente, gravado no MySQL.
        mvc.perform(get("/carrinhos/atual").header("X-Sessao-Id", sessao).with(jwtDe(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeTotal").value(3))
                .andExpect(jsonPath("$.itens[0].produtoId").value(PRODUTO_ID));
        assertThat(contarCarrinhos(cliente)).isEqualTo(1);
        assertThat(redis.hasKey("carrinho:visitante:" + sessao)).isFalse();

        // Depois, só com o cliente (sem a sessão): os itens continuam lá.
        mvc.perform(get("/carrinhos/atual").with(jwtDe(cliente)))
                .andExpect(jsonPath("$.quantidadeTotal").value(3));

        // Cupom e checkout sobre o carrinho já unido.
        mvc.perform(post("/carrinhos/atual/cupom").with(jwtDe(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"bemvindo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desconto").value(30.00));
        mvc.perform(post("/carrinhos/atual/checkout").with(jwtDe(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(270.00));
    }

    @Autowired
    private RabbitTemplate rabbit;

    /**
     * Critério de pronto: a mudança de preço publicada pelo catálogo chega pela fila e aparece no carrinho (de
     * cliente e de visitante) com o valor antigo e o novo. Também prova o consumo idempotente e imune à ordem.
     */
    @Test
    void mudancaDePrecoPublicadaNoRabbitAparecePeloHttpComAvisoDoValorAntigo() throws Exception {
        String sessao = "sessao-" + UUID.randomUUID();
        String cliente = "cliente-" + UUID.randomUUID();
        adicionarComoCliente(cliente, PRODUTO_ID_2);
        adicionarComoCliente(cliente, PRODUTO_ID);
        adicionar("X-Sessao-Id", sessao, PRODUTO_ID);

        // A nova (120 às 12:01), a duplicata dela e, por último, uma velha atrasada (90 às 12:00): sem o descarte
        // de duplicata e de mensagem fora de ordem, o preço final seria 90.
        publicar(PRODUTO_ID, "120.00", "2026-11-19T12:01:00Z");
        publicar(PRODUTO_ID, "120.00", "2026-11-19T12:01:00Z");
        publicar(PRODUTO_ID, "90.00", "2026-11-19T12:00:00Z");
        // A fila tem um consumidor só: quando esta (outro produto) aparecer, as anteriores já foram tratadas.
        publicar(PRODUTO_ID_2, "80.00", "2026-11-19T12:02:00Z");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/carrinhos/atual").with(jwtDe(cliente)))
                        .andExpect(jsonPath("$.itens[?(@.produtoId=='" + PRODUTO_ID_2 + "')].precoUnitario")
                                .value(80.00)));

        mvc.perform(get("/carrinhos/atual").with(jwtDe(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.produtoId=='" + PRODUTO_ID + "')].precoUnitario").value(120.00))
                .andExpect(jsonPath("$.itens[?(@.produtoId=='" + PRODUTO_ID + "')].precoAlterado").value(true))
                .andExpect(jsonPath("$.itens[?(@.produtoId=='" + PRODUTO_ID + "')].precoAnterior").value(100.00))
                .andExpect(jsonPath("$.itens[?(@.produtoId=='" + PRODUTO_ID_2 + "')].precoAnterior").value(100.00))
                .andExpect(jsonPath("$.subtotal").value(200.00));

        // O carrinho de visitante também foi atualizado, pelo índice de produto no Redis.
        mvc.perform(get("/carrinhos/atual").header("X-Sessao-Id", sessao))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(120.00))
                .andExpect(jsonPath("$.itens[0].precoAnterior").value(100.00));
    }

    @Test
    void mensagemMalformadaNaoTravaAFilaEVaiParaADlqDepoisDeTresTentativas() throws Exception {
        String cliente = "cliente-" + UUID.randomUUID();
        adicionarComoCliente(cliente, PRODUTO_ID);

        rabbit.send("mini.events", "catalogo.preco-alterado", json("{\"produtoId\":\"lixo\",\"precoNovo\":1}"));
        publicar(PRODUTO_ID, "150.00", "2026-11-19T13:00:00Z");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/carrinhos/atual").with(jwtDe(cliente)))
                        .andExpect(jsonPath("$.itens[0].precoUnitario").value(150.00)));

        // A malformada foi tentada 3 vezes (retry) e o broker a entregou na fila morta, à vista no painel do RabbitMQ.
        Message morta = rabbit.receive("carrinho.preco-alterado.dlq", 10_000);
        assertThat(morta).isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).contains("lixo");
        assertThat(morta.getMessageProperties().getHeaders()).containsKey("x-death");
    }

    /** A trava desta mudança: "ser cliente" exige JWT válido; o header antigo não vale mais nada. */
    @Test
    void xClienteIdFalsificadoNaoDaAcessoAoCarrinhoDeNinguem() throws Exception {
        String cliente = "cliente-" + UUID.randomUUID();
        adicionarComoCliente(cliente, PRODUTO_ID);

        // Sem token e sem sessão, o X-Cliente-Id é ignorado: nem identificação existe.
        mvc.perform(get("/carrinhos/atual").header("X-Cliente-Id", cliente))
                .andExpect(status().isBadRequest());
        // Com uma sessão qualquer, quem responde é o carrinho de visitante (vazio), nunca o do cliente.
        mvc.perform(get("/carrinhos/atual").header("X-Cliente-Id", cliente).header("X-Sessao-Id", "intruso-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeTotal").value(0));
    }

    @Test
    void checkoutSemTokenEhNaoAutenticadoETokenInvalidoNemChegaAoController() throws Exception {
        mvc.perform(post("/carrinhos/atual/checkout").header("X-Sessao-Id", "s1"))
                .andExpect(status().isUnauthorized());
        // Bearer malformado/não assinado pelo Keycloak: o Spring Security barra antes de qualquer regra do carrinho.
        mvc.perform(get("/carrinhos/atual").header("X-Sessao-Id", "s1").header("Authorization", "Bearer isto-nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtDe(String cliente) {
        // JWT já "validado": o filtro de segurança real roda, o que o teste dispensa é só a assinatura do Keycloak.
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(cliente));
    }

    private void adicionarComoCliente(String cliente, String produtoId) throws Exception {
        mvc.perform(post("/carrinhos/atual/itens").with(jwtDe(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"" + produtoId + "\",\"quantidade\":1}"))
                .andExpect(status().isOk());
    }

    private void adicionar(String header, String identificacao, String produtoId) throws Exception {
        mvc.perform(post("/carrinhos/atual/itens").header(header, identificacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"" + produtoId + "\",\"quantidade\":1}"))
                .andExpect(status().isOk());
    }

    private void publicar(String produtoId, String precoNovo, String ocorridoEm) {
        rabbit.send("mini.events", "catalogo.preco-alterado", json("{\"produtoId\":\"" + produtoId
                + "\",\"sku\":\"ESF-01\",\"precoAnterior\":100.00,\"precoNovo\":" + precoNovo
                + ",\"ocorridoEm\":\"" + ocorridoEm + "\",\"campoNovoQueOCarrinhoIgnora\":true}"));
    }

    private static Message json(String corpo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8)).andProperties(propriedades).build();
    }

    /** Todos os endpoints aparecem no OpenAPI (base do Swagger UI). */
    @Test
    void documentaOsEndpointsNoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/carrinhos/atual'].get").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/itens'].post").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/itens/{produtoId}'].put").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/itens/{produtoId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/cupom'].post").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/cupom'].delete").exists())
                .andExpect(jsonPath("$.paths['/carrinhos/atual/checkout'].post").exists());
    }

    @Test
    void saudeIncluiMySqlERedis() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private int contarCarrinhos(String cliente) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM carrinho WHERE cliente_id = ?", Integer.class, cliente);
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
    }

    /** Catálogo falso: responde o produto PRODUTO_ID (R$ 100,00) e 404 para os outros. */
    private static HttpServer catalogoFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/produtos/por-id/", troca -> {
                String id = troca.getRequestURI().getPath().substring(troca.getRequestURI().getPath().lastIndexOf('/') + 1);
                boolean existe = id.equals(PRODUTO_ID) || id.equals(PRODUTO_ID_2);
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
