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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o carrinho-service inteiro (com o application.yml real) contra MySQL 8 e Redis 7 em containers,
 * com um catálogo falso. O Flyway cria o schema, o Hibernate valida as entidades e o critério de pronto
 * da issue é provado pelo HTTP: itens adicionados como visitante continuam no carrinho depois do login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CarrinhoIntegracaoTest.Infra.class)
class CarrinhoIntegracaoTest {

    private static final String PRODUTO_ID = UUID.randomUUID().toString();
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
        mvc.perform(get("/carrinhos/atual").header("X-Sessao-Id", sessao).header("X-Cliente-Id", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeTotal").value(3))
                .andExpect(jsonPath("$.itens[0].produtoId").value(PRODUTO_ID));
        assertThat(contarCarrinhos(cliente)).isEqualTo(1);
        assertThat(redis.hasKey("carrinho:visitante:" + sessao)).isFalse();

        // Depois, só com o cliente (sem a sessão): os itens continuam lá.
        mvc.perform(get("/carrinhos/atual").header("X-Cliente-Id", cliente))
                .andExpect(jsonPath("$.quantidadeTotal").value(3));

        // Cupom e checkout sobre o carrinho já unido.
        mvc.perform(post("/carrinhos/atual/cupom").header("X-Cliente-Id", cliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"bemvindo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desconto").value(30.00));
        mvc.perform(post("/carrinhos/atual/checkout").header("X-Cliente-Id", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(270.00));
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
    }

    /** Catálogo falso: responde o produto PRODUTO_ID (R$ 100,00) e 404 para os outros. */
    private static HttpServer catalogoFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/produtos/por-id/", troca -> {
                boolean existe = troca.getRequestURI().getPath().endsWith("/" + PRODUTO_ID);
                String corpo = existe
                        ? "{\"id\":\"" + PRODUTO_ID + "\",\"nome\":\"Esfera Chrome\",\"preco\":100.00,\"ativo\":true}"
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
