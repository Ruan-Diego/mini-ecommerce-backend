package com.ruandiego.mini.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o gateway com o application.yml real e um catálogo falso (servidor HTTP do JDK) no lugar do
 * catalogo-service. Prova a rota (prefixo /api/catalogo removido) e o CORS das portas dos microfrontends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRotasTest {

    private static final AtomicReference<String> CAMINHO_RECEBIDO = new AtomicReference<>();
    private static final HttpServer CATALOGO_FALSO = servidorFalso(CAMINHO_RECEBIDO);
    private static final AtomicReference<String> CARRINHO_RECEBIDO = new AtomicReference<>();
    private static final HttpServer CARRINHO_FALSO = servidorFalso(CARRINHO_RECEBIDO);

    @DynamicPropertySource
    static void apontarParaOCatalogoFalso(DynamicPropertyRegistry registro) {
        registro.add("CATALOGO_URL", () -> "http://localhost:" + CATALOGO_FALSO.getAddress().getPort());
        registro.add("CARRINHO_URL", () -> "http://localhost:" + CARRINHO_FALSO.getAddress().getPort());
    }

    @AfterAll
    static void pararCatalogoFalso() {
        CATALOGO_FALSO.stop(0);
        CARRINHO_FALSO.stop(0);
    }

    @Autowired
    private WebTestClient cliente;

    @Test
    void repassaParaOCatalogoSemOPrefixo() {
        cliente.get().uri("/api/catalogo/produtos?ordem=menor-preco").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"itens\":[]}");

        assertThat(CAMINHO_RECEBIDO.get()).isEqualTo("GET /produtos?ordem=menor-preco sessao=null");
    }

    @Test
    void repassaParaOCarrinhoSemOPrefixoERepassaOHeaderDaSessao() {
        cliente.put().uri("/api/carrinho/carrinhos/atual/itens/abc")
                .header("X-Sessao-Id", "sessao-1")
                .exchange()
                .expectStatus().isOk();

        assertThat(CARRINHO_RECEBIDO.get()).isEqualTo("PUT /carrinhos/atual/itens/abc sessao=sessao-1");
    }

    @Test
    void liberaCorsDoCarrinhoParaMetodosEHeadersDaSessao() {
        cliente.options().uri("/api/carrinho/carrinhos/atual/itens/abc")
                .header(HttpHeaders.ORIGIN, "http://localhost:5175")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.DELETE.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-sessao-id,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5175");
    }

    @Test
    void liberaCorsParaOsMicrofrontends() {
        cliente.options().uri("/api/catalogo/produtos")
                .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174");
    }

    @Test
    void recusaCorsDeOutraOrigem() {
        cliente.options().uri("/api/catalogo/produtos")
                .header(HttpHeaders.ORIGIN, "http://exemplo.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    /** Gateway e serviço devolvem o X-Trace-Id; a resposta sai com um só (DedupeResponseHeader). */
    @Test
    void respostaTemUmSoTraceId() {
        cliente.get().uri("/api/catalogo/produtos").exchange()
                .expectStatus().isOk()
                .expectHeader().values("X-Trace-Id", valores -> assertThat(valores).hasSize(1));
    }

    @Test
    void expoeHealthParaOHealthcheckDoCompose() {
        cliente.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    private static HttpServer servidorFalso(AtomicReference<String> caminhoRecebido) {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
                caminhoRecebido.set(troca.getRequestMethod() + " " + troca.getRequestURI() + " sessao=" + troca.getRequestHeaders().getFirst("X-Sessao-Id"));
                byte[] corpo = "{\"itens\":[]}".getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                // Como os serviços reais (TraceIdFilter): devolve o X-Trace-Id que recebeu.
                String traceId = troca.getRequestHeaders().getFirst("X-Trace-Id");
                if (traceId != null) {
                    troca.getResponseHeaders().add("X-Trace-Id", traceId);
                }
                troca.sendResponseHeaders(200, corpo.length);
                troca.getResponseBody().write(corpo);
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
