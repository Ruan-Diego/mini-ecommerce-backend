package com.ruandiego.mini.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A saga completa, ponta a ponta: pedido-service, estoque-service e pagamento-service de verdade (os jars
 * executáveis, cada um num processo), conversando só pelo RabbitMQ e cada um com o próprio schema no mesmo
 * MySQL, tudo em containers. O teste só fala com eles como o gateway falaria (HTTP com JWT) e confere o resultado
 * pelas APIs e pelos bancos.
 *
 * Os quatro cenários da issue: compra aprovada, pagamento recusado, estoque indisponível e expiração do pedido.
 * Mais um: cancelar um pedido já pago estorna o pagamento.
 *
 * Os serviços rodam com os agendadores reais (relay da outbox a cada 200 ms, job de expiração a cada 500 ms).
 * Para a expiração, o teste recua a data de criação do pedido em 31 minutos direto no banco, no lugar de esperar.
 */
class SagaTest {

    private static final String CARTAO_APROVADO = "4111111111111112"; // gateway fake: último dígito par aprova
    private static final String CARTAO_RECUSADO = "4111111111111113"; // e ímpar recusa
    private static final Duration ESPERA = Duration.ofSeconds(45);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management");
    private static final Map<String, Integer> PORTAS = new LinkedHashMap<>();
    private static final List<Process> PROCESSOS = new ArrayList<>();

    private static RSAKey chave;
    private static HttpServer jwks;
    private static HttpServer catalogo;
    private static String emissor;
    private static JdbcTemplate dbPedido;
    private static JdbcTemplate dbPagamento;

    // ---------------------------------------------------------------- ambiente

    @BeforeAll
    static void subirTudo() throws Exception {
        MYSQL.start();
        RABBITMQ.start();
        criarSchemas();
        dbPedido = jdbc("pedido");
        dbPagamento = jdbc("pagamento");

        chave = new RSAKeyGenerator(2048).keyID("saga").generate();
        jwks = servidor(troca -> responder(troca, 200, "{\"keys\":[" + chave.toPublicJWK().toJSONString() + "]}"));
        emissor = "http://localhost:" + jwks.getAddress().getPort();
        // Catálogo falso: qualquer produto existe, custa R$ 100,00.
        catalogo = servidor(troca -> {
            String caminho = troca.getRequestURI().getPath();
            String id = caminho.substring(caminho.lastIndexOf('/') + 1);
            responder(troca, 200, "{\"id\":\"" + id + "\",\"nome\":\"Esfera da saga\",\"preco\":100.00,\"ativo\":true}");
        });

        for (String servico : List.of("pedido", "estoque", "pagamento")) {
            PORTAS.put(servico, portaLivre());
        }
        Map<String, String> comum = new LinkedHashMap<>();
        comum.put("DB_HOST", MYSQL.getHost());
        comum.put("DB_PORT", String.valueOf(MYSQL.getMappedPort(3306)));
        comum.put("DB_USER", "root");
        comum.put("DB_PASSWORD", MYSQL.getPassword());
        comum.put("RABBITMQ_HOST", RABBITMQ.getHost());
        comum.put("RABBITMQ_PORT", String.valueOf(RABBITMQ.getAmqpPort()));
        comum.put("KEYCLOAK_ISSUER", emissor);
        comum.put("KEYCLOAK_JWKS", emissor + "/certs");

        iniciar("pedido", comum, Map.of(
                "CATALOGO_URL", "http://localhost:" + catalogo.getAddress().getPort(),
                "PEDIDO_OUTBOX_INTERVALO", "200",
                "PEDIDO_EXPIRACAO_INTERVALO", "500"));
        iniciar("estoque", comum, Map.of("ESTOQUE_OUTBOX_INTERVALO", "200"));
        iniciar("pagamento", comum, Map.of("PAGAMENTO_OUTBOX_INTERVALO", "200"));
        for (String servico : PORTAS.keySet()) {
            aguardarSaude(servico);
        }
    }

    @AfterAll
    static void descerTudo() {
        PROCESSOS.forEach(Process::destroy);
        PROCESSOS.forEach(p -> {
            try {
                if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        if (jwks != null) {
            jwks.stop(0);
        }
        if (catalogo != null) {
            catalogo.stop(0);
        }
        RABBITMQ.stop();
        MYSQL.stop();
    }

    private static void criarSchemas() {
        DriverManagerDataSource raiz = new DriverManagerDataSource(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/", "root", MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(raiz);
        for (String schema : List.of("pedido", "estoque", "pagamento")) {
            jdbc.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + schema,
                "root", MYSQL.getPassword()));
    }

    private static void iniciar(String servico, Map<String, String> comum, Map<String, String> proprio) throws IOException {
        String jar = System.getProperty("saga.jar." + servico);
        assertThat(new File(jar)).as("jar executável de %s (a tarefa bootJar roda antes dos testes)", servico).exists();
        Path logs = Path.of(System.getProperty("saga.logs", "build/logs"));
        Files.createDirectories(logs);

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder construtor = new ProcessBuilder(java, "-Xmx320m", "-jar", jar);
        construtor.environment().putAll(comum);
        construtor.environment().putAll(proprio);
        construtor.environment().put("SERVER_PORT", String.valueOf(PORTAS.get(servico)));
        construtor.redirectErrorStream(true);
        construtor.redirectOutput(logs.resolve(servico + ".log").toFile());
        PROCESSOS.add(construtor.start());
    }

    private static void aguardarSaude(String servico) {
        Process processo = PROCESSOS.get(new ArrayList<>(PORTAS.keySet()).indexOf(servico));
        Instant limite = Instant.now().plus(Duration.ofSeconds(180));
        while (Instant.now().isBefore(limite)) {
            if (!processo.isAlive()) {
                throw new IllegalStateException(servico + " encerrou ao subir. Log:\n" + fimDoLog(servico));
            }
            try {
                if (requisitar("GET", servico, "/actuator/health", null, null).statusCode() == 200) {
                    return;
                }
            } catch (RuntimeException e) {
                // ainda subindo
            }
            dormir(1_000);
        }
        throw new IllegalStateException(servico + " não ficou saudável em 180 s. Log:\n" + fimDoLog(servico));
    }

    private static String fimDoLog(String servico) {
        try {
            List<String> linhas = Files.readAllLines(Path.of(System.getProperty("saga.logs", "build/logs"), servico + ".log"));
            return String.join("\n", linhas.subList(Math.max(0, linhas.size() - 40), linhas.size()));
        } catch (IOException e) {
            return "(sem log)";
        }
    }

    // ---------------------------------------------------------------- cenários

    @Test
    void compraAprovada_reservaOEstoqueEDepoisFazABaixaDefinitiva() throws Exception {
        UUID produto = UUID.randomUUID();
        definirEstoque(produto, 5);

        Pedido pedido = criarPedido("ana", produto, 2);
        aguardarSaldo(produto, 3, 2); // estoque.reservado: separou 2

        JsonNode pagamento = pagar("ana", pedido, CARTAO_APROVADO);
        assertThat(pagamento.get("status").asText()).isEqualTo("APROVADO");

        aguardarStatusDoPedido("ana", pedido, "PAGO");
        aguardarSaldo(produto, 3, 0); // pedido.pago: a baixa virou definitiva
    }

    @Test
    void pagamentoRecusado_cancelaOPedidoEDevolveOEstoque() throws Exception {
        UUID produto = UUID.randomUUID();
        definirEstoque(produto, 5);
        Pedido pedido = criarPedido("ana", produto, 2);
        aguardarSaldo(produto, 3, 2);

        JsonNode pagamento = pagar("ana", pedido, CARTAO_RECUSADO);
        assertThat(pagamento.get("status").asText()).isEqualTo("RECUSADO");

        aguardarStatusDoPedido("ana", pedido, "CANCELADO"); // pagamento.recusado
        aguardarSaldo(produto, 5, 0); // pedido.cancelado: o estoque voltou (critério de pronto da Fase 4)

        // O pedido está cancelado: pagar de novo é recusado e não cobra nada.
        assertThat(tentarPagar("ana", pedido, CARTAO_APROVADO).statusCode()).isEqualTo(409);
        assertThat(dbPagamento.queryForObject("SELECT COUNT(*) FROM pagamento WHERE pedido_id = ?", Integer.class, pedido.id()))
                .isEqualTo(1);
    }

    @Test
    void estoqueIndisponivel_cancelaOPedidoSemSepararNada() throws Exception {
        UUID produto = UUID.randomUUID();
        definirEstoque(produto, 1);

        Pedido pedido = criarPedido("ana", produto, 3); // pede 3, só há 1

        aguardarStatusDoPedido("ana", pedido, "CANCELADO"); // estoque.indisponivel
        JsonNode saldo = saldo(produto);
        assertThat(saldo.get("disponivel").asInt()).isEqualTo(1);
        assertThat(saldo.get("reservado").asInt()).isZero();

        // O pagamento fica sabendo do cancelamento e passa a recusar a cobrança desse pedido.
        await().atMost(ESPERA).untilAsserted(() -> assertThat(dbPagamento.queryForObject(
                "SELECT COUNT(*) FROM pedido_cancelado WHERE pedido_id = ?", Integer.class, pedido.id())).isEqualTo(1));
        assertThat(tentarPagar("ana", pedido, CARTAO_APROVADO).statusCode()).isEqualTo(409);
    }

    @Test
    void expiracao_pedidoSemPagamentoEm30MinutosCancelaEDevolveOEstoque() throws Exception {
        UUID produto = UUID.randomUUID();
        definirEstoque(produto, 5);
        Pedido pedido = criarPedido("ana", produto, 2);
        aguardarSaldo(produto, 3, 2);

        // O job do pedido roda a cada 500 ms: basta o pedido parecer ter 31 minutos.
        dbPedido.update("UPDATE pedido SET criado_em = DATE_SUB(criado_em, INTERVAL 31 MINUTE) WHERE id = ?", pedido.id());

        aguardarStatusDoPedido("ana", pedido, "CANCELADO");
        aguardarSaldo(produto, 5, 0);
    }

    @Test
    void cancelarUmPedidoJaPago_estornaOPagamento() throws Exception {
        UUID produto = UUID.randomUUID();
        definirEstoque(produto, 5);
        Pedido pedido = criarPedido("ana", produto, 1);
        aguardarSaldo(produto, 4, 1);
        pagar("ana", pedido, CARTAO_APROVADO);
        aguardarStatusDoPedido("ana", pedido, "PAGO");

        HttpResponse<String> cancelamento = requisitar("POST", "pedido", "/pedidos/" + pedido.id() + "/cancelar",
                token("ana", List.of("cliente")), null);
        assertThat(cancelamento.statusCode()).isEqualTo(200);

        await().atMost(ESPERA).untilAsserted(() -> assertThat(dbPagamento.queryForObject(
                "SELECT status FROM pagamento WHERE pedido_id = ?", String.class, pedido.id())).isEqualTo("ESTORNADO"));
        aguardarStatusDoPedido("ana", pedido, "CANCELADO");
    }

    // ---------------------------------------------------------------- operações

    private record Pedido(String id, BigDecimal total) {
    }

    private static Pedido criarPedido(String cliente, UUID produto, int quantidade) throws Exception {
        String corpo = """
                {"itens":[{"produtoId":"%s","quantidade":%d,"precoVisto":100.00}],
                 "endereco":{"destinatario":"Ana Souza","cep":"01310-100","logradouro":"Av. Paulista","numero":"1000",
                             "bairro":"Bela Vista","cidade":"São Paulo","uf":"SP"},
                 "freteCodigo":"padrao"}""".formatted(produto, quantidade);
        HttpResponse<String> resposta = requisitar("POST", "pedido", "/pedidos", token(cliente, List.of("cliente")), corpo);
        assertThat(resposta.statusCode()).as("criar pedido: %s", resposta.body()).isEqualTo(201);
        JsonNode pedido = JSON.readTree(resposta.body());
        return new Pedido(pedido.get("id").asText(), pedido.get("total").decimalValue());
    }

    private static HttpResponse<String> tentarPagar(String cliente, Pedido pedido, String cartao) {
        String corpo = "{\"pedidoId\":\"%s\",\"valor\":%s,\"metodo\":\"CARTAO\",\"numeroCartao\":\"%s\"}"
                .formatted(pedido.id(), pedido.total().toPlainString(), cartao);
        return requisitar("POST", "pagamento", "/pagamentos", token(cliente, List.of("cliente")), corpo);
    }

    private static JsonNode pagar(String cliente, Pedido pedido, String cartao) throws Exception {
        HttpResponse<String> resposta = tentarPagar(cliente, pedido, cartao);
        assertThat(resposta.statusCode()).as("pagar: %s", resposta.body()).isEqualTo(201);
        return JSON.readTree(resposta.body());
    }

    private static void definirEstoque(UUID produto, int disponivel) {
        HttpResponse<String> resposta = requisitar("PUT", "estoque", "/admin/estoque/" + produto,
                token("admin", List.of("admin")), "{\"disponivel\":" + disponivel + "}");
        assertThat(resposta.statusCode()).as("definir estoque: %s", resposta.body()).isEqualTo(200);
    }

    private static JsonNode saldo(UUID produto) throws Exception {
        return JSON.readTree(requisitar("GET", "estoque", "/estoque/" + produto, null, null).body());
    }

    private static void aguardarSaldo(UUID produto, int disponivel, int reservado) {
        await().atMost(ESPERA).untilAsserted(() -> {
            JsonNode saldo = saldo(produto);
            assertThat(saldo.get("disponivel").asInt()).as("disponível").isEqualTo(disponivel);
            assertThat(saldo.get("reservado").asInt()).as("reservado").isEqualTo(reservado);
        });
    }

    private static void aguardarStatusDoPedido(String cliente, Pedido pedido, String status) {
        await().atMost(ESPERA).untilAsserted(() -> {
            HttpResponse<String> resposta = requisitar("GET", "pedido", "/pedidos/" + pedido.id(),
                    token(cliente, List.of("cliente")), null);
            assertThat(JSON.readTree(resposta.body()).get("status").asText()).as("status do pedido").isEqualTo(status);
        });
    }

    // ---------------------------------------------------------------- infraestrutura de teste

    private static HttpResponse<String> requisitar(String metodo, String servico, String caminho, String token, String corpo) {
        HttpRequest.Builder construtor = HttpRequest.newBuilder(URI.create("http://localhost:" + PORTAS.get(servico) + caminho))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        if (corpo != null) {
            construtor.header("Content-Type", "application/json");
        }
        if (token != null) {
            construtor.header("Authorization", "Bearer " + token);
        }
        try {
            return HTTP.send(construtor.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String token(String sujeito, List<String> papeis) {
        try {
            Instant agora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(emissor)
                    .subject(sujeito)
                    .issueTime(Date.from(agora.minusSeconds(60)))
                    .expirationTime(Date.from(agora.plusSeconds(600)))
                    .claim("realm_access", Map.of("roles", papeis))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(chave));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private interface Atendente {
        void atender(com.sun.net.httpserver.HttpExchange troca) throws IOException;
    }

    private static HttpServer servidor(Atendente atendente) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", troca -> {
            try {
                atendente.atender(troca);
            } catch (IOException | RuntimeException e) {
                troca.close();
            }
        });
        servidor.start();
        return servidor;
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    private static int portaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
