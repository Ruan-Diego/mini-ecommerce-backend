package com.ruandiego.mini.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * O gateway é a porta de entrada do trace id: gera um quando a requisição não traz, mantém o que vier num formato
 * seguro, troca o que vier estranho, repassa ao serviço e devolve na resposta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTraceIdTest {

    private static final AtomicReference<String> TRACE_ID_RECEBIDO = new AtomicReference<>();
    private static final HttpServer CATALOGO_FALSO = servidorQueAnotaOTraceId();

    @DynamicPropertySource
    static void apontar(DynamicPropertyRegistry registro) {
        registro.add("CATALOGO_URL", () -> "http://localhost:" + CATALOGO_FALSO.getAddress().getPort());
    }

    @AfterAll
    static void parar() {
        CATALOGO_FALSO.stop(0);
    }

    @Autowired
    private WebTestClient cliente;

    @Test
    void geraUmTraceIdQuandoFaltaERepassaAoServicoENaResposta() {
        String naResposta = cliente.get().uri("/api/catalogo/produtos").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Trace-Id");

        assertThat(naResposta).matches("[0-9a-f]{32}");
        assertThat(TRACE_ID_RECEBIDO.get()).isEqualTo(naResposta);
    }

    @Test
    void mantemOTraceIdRecebido() {
        cliente.get().uri("/api/catalogo/produtos").header("X-Trace-Id", "meu-trace.123").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", "meu-trace.123");

        assertThat(TRACE_ID_RECEBIDO.get()).isEqualTo("meu-trace.123");
    }

    @Test
    void trocaUmTraceIdComFormatoEstranho() {
        String naResposta = cliente.get().uri("/api/catalogo/produtos").header("X-Trace-Id", "falso INFO login ok").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Trace-Id");

        assertThat(naResposta).matches("[0-9a-f]{32}");
        assertThat(TRACE_ID_RECEBIDO.get()).isEqualTo(naResposta);
    }

    private static HttpServer servidorQueAnotaOTraceId() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
                TRACE_ID_RECEBIDO.set(troca.getRequestHeaders().getFirst("X-Trace-Id"));
                byte[] corpo = "{\"itens\":[]}".getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
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
