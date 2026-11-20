package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.ServicoIndisponivelException;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Melhor Envio falso (JDK HttpServer): confere o pedido enviado, a leitura das opções, o retry e o fallback. */
class MelhorEnvioAdapterTest {

    private static final String RESPOSTA = """
            [{"id":1,"name":"PAC","price":"23.45","delivery_time":6,"company":{"name":"Correios"}},
             {"id":2,"name":"SEDEX","price":"41.10","delivery_time":2,"company":{"name":"Correios"}},
             {"id":3,"name":".Package","error":"Serviço indisponível para o trecho","company":{"name":"Jadlog"}}]
            """;

    private HttpServer servidor;
    private final List<String> requisicoes = new CopyOnWriteArrayList<>();
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile int falhasAntesDeResponder = 0;
    private volatile String resposta = RESPOSTA;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v2/me/shipment/calculate", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requisicoes.add(troca.getRequestMethod() + " auth=" + troca.getRequestHeaders().getFirst("Authorization")
                    + " ua=" + troca.getRequestHeaders().getFirst("User-Agent") + " " + corpo);
            int numero = chamadas.incrementAndGet();
            byte[] bytes = (numero <= falhasAntesDeResponder ? "{}" : resposta).getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(numero <= falhasAntesDeResponder ? 503 : 200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private MelhorEnvioAdapter adapter() {
        return new MelhorEnvioAdapter("http://127.0.0.1:" + servidor.getAddress().getPort(), "TOKEN-TESTE",
                "Mini Ecommerce (teste@exemplo.com)", "01001000",
                new MelhorEnvioAdapter.Pacote(10, 20, 30, new BigDecimal("1")),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void cotaEMapeiaAsOpcoesIgnorandoAsComErro() {
        List<Frete> fretes = adapter().cotar("20040020", Dinheiro.de("120.00"));

        assertThat(fretes).extracting(Frete::codigo).containsExactly("me-1", "me-2");
        assertThat(fretes.get(0).nome()).isEqualTo("Correios PAC");
        assertThat(fretes.get(0).valor()).isEqualTo(Dinheiro.de("23.45"));
        assertThat(fretes.get(0).prazoDias()).isEqualTo(6);
        assertThat(requisicoes.get(0)).contains("auth=Bearer TOKEN-TESTE").contains("ua=Mini Ecommerce (teste@exemplo.com)")
                .contains("\"postal_code\":\"01001000\"").contains("\"postal_code\":\"20040020\"")
                .contains("\"insurance_value\":120.00");
    }

    @Test
    void semOpcaoUtilizavelEIndisponivel() {
        resposta = "[{\"id\":3,\"name\":\".Package\",\"error\":\"x\"}]";

        assertThatThrownBy(() -> adapter().cotar("20040020", Dinheiro.de("10.00")))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void retryRecuperaDeFalhaPassageira() {
        falhasAntesDeResponder = 2;
        FreteComFallback frete = new FreteComFallback(adapter(), new FreteFixoAdapter(), 3, Duration.ofMillis(10));

        List<Frete> fretes = frete.cotar("20040020", Dinheiro.de("120.00"));

        assertThat(fretes).extracting(Frete::codigo).containsExactly("me-1", "me-2");
        assertThat(chamadas.get()).isEqualTo(3);
    }

    @Test
    void esgotadasAsTentativasCaiNoFreteFixo() {
        falhasAntesDeResponder = Integer.MAX_VALUE;
        FreteComFallback frete = new FreteComFallback(adapter(), new FreteFixoAdapter(), 3, Duration.ofMillis(10));

        List<Frete> fretes = frete.cotar("20040020", Dinheiro.de("120.00"));

        assertThat(fretes).extracting(Frete::codigo).containsExactly("padrao", "expresso");
        assertThat(chamadas.get()).isEqualTo(3);
    }

    @Test
    void servidorForaDoArCaiNoFreteFixo() {
        servidor.stop(0);
        FreteComFallback frete = new FreteComFallback(adapter(), new FreteFixoAdapter(), 2, Duration.ofMillis(10));

        assertThat(frete.cotar("20040020", Dinheiro.de("500.00")).get(0).valor()).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void erroQueNaoEDeIndisponibilidadeNaoEEngolido() {
        com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete quebrado = (cep, subtotal) -> {
            throw new IllegalStateException("bug");
        };
        FreteComFallback frete = new FreteComFallback(quebrado, new FreteFixoAdapter(), 3, Duration.ofMillis(10));

        assertThatThrownBy(() -> frete.cotar("20040020", Dinheiro.de("10.00"))).isInstanceOf(IllegalStateException.class);
    }
}
