package com.ruandiego.mini.pagamento.adapters.out.gateway;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.ServicoIndisponivelException;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mercado Pago falso (JDK HttpServer): confere o que o adapter envia e como ele lê as respostas. */
class MercadoPagoSandboxAdapterTest {

    private HttpServer servidor;
    private final List<String> requisicoes = new CopyOnWriteArrayList<>();
    private volatile String statusDoPagamento = "approved";
    private volatile int codigoDoPagamento = 201;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String chamada = troca.getRequestMethod() + " " + troca.getRequestURI() + " auth="
                    + troca.getRequestHeaders().getFirst("Authorization") + " " + corpo;
            requisicoes.add(chamada);
            String caminho = troca.getRequestURI().getPath();
            String resposta;
            int codigo = 200;
            if (caminho.equals("/v1/card_tokens")) {
                resposta = "{\"id\":\"tok_123\"}";
            } else if (caminho.equals("/v1/payments") && troca.getRequestMethod().equals("POST")) {
                codigo = codigoDoPagamento;
                resposta = corpo.contains("\"pix\"")
                        ? "{\"id\":98765,\"status\":\"pending\",\"point_of_interaction\":{\"transaction_data\":{\"qr_code\":\"000201PIXMP\"}}}"
                        : "{\"id\":55555,\"status\":\"" + statusDoPagamento + "\",\"status_detail\":\"cc_rejected_other_reason\"}";
            } else if (caminho.endsWith("/refunds")) {
                resposta = "{\"id\":1}";
            } else {
                resposta = "{\"id\":98765,\"status\":\"" + statusDoPagamento + "\"}";
            }
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(codigo, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private MercadoPagoSandboxAdapter adapter() {
        return new MercadoPagoSandboxAdapter("http://127.0.0.1:" + servidor.getAddress().getPort(), "TEST-token",
                "TEST-publica", "comprador@testuser.com", "https://exemplo.test/api/pagamentos/webhooks/gateway");
    }

    @Test
    void cartaoDeFinalParAprovaComTitularAPRO() {
        GatewayPagamento.ResultadoCartao resultado = adapter().cobrarCartao(new Dinheiro(new BigDecimal("100.00")), "5031433215406352");

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.transacaoId()).isEqualTo("55555");
        assertThat(requisicoes.get(0)).contains("POST /v1/card_tokens?public_key=TEST-publica").contains("\"APRO\"");
        assertThat(requisicoes.get(1)).contains("auth=Bearer TEST-token").contains("\"token\":\"tok_123\"")
                .contains("\"transaction_amount\":100.00");
    }

    @Test
    void cartaoDeFinalImparUsaTitularOTHEERecusa() {
        statusDoPagamento = "rejected";

        GatewayPagamento.ResultadoCartao resultado = adapter().cobrarCartao(new Dinheiro(new BigDecimal("100.00")), "5031433215406351");

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.motivoRecusa()).contains("rejected").contains("cc_rejected_other_reason");
        assertThat(requisicoes.get(0)).contains("\"OTHE\"");
    }

    @Test
    void pixDevolveTransacaoECodigoENotificaNaUrlConfigurada() {
        GatewayPagamento.CobrancaPix pix = adapter().gerarPix(new Dinheiro(new BigDecimal("50.00")), Instant.parse("2026-11-20T12:30:00Z"));

        assertThat(pix.transacaoId()).isEqualTo("98765");
        assertThat(pix.codigoCopiaECola()).isEqualTo("000201PIXMP");
        assertThat(requisicoes.get(0)).contains("\"payment_method_id\":\"pix\"")
                .contains("\"date_of_expiration\":\"2026-11-20T12:30:00.000+00:00\"")
                .contains("\"notification_url\":\"https://exemplo.test/api/pagamentos/webhooks/gateway\"");
    }

    @Test
    void estornoChamaORefundDaTransacao() {
        adapter().estornar("98765");

        assertThat(requisicoes.get(0)).startsWith("POST /v1/payments/98765/refunds");
    }

    @Test
    void consultaTraduzOStatusParaEvento() {
        statusDoPagamento = "approved";
        assertThat(adapter().consultarEvento("98765")).contains(Evento.APROVADO);
        statusDoPagamento = "cancelled";
        assertThat(adapter().consultarEvento("98765")).contains(Evento.RECUSADO);
        statusDoPagamento = "refunded";
        assertThat(adapter().consultarEvento("98765")).contains(Evento.ESTORNADO);
        statusDoPagamento = "pending";
        assertThat(adapter().consultarEvento("98765")).isEqualTo(Optional.empty());
    }

    @Test
    void erroDoMercadoPagoVira503NoRest() {
        codigoDoPagamento = 500;

        assertThatThrownBy(() -> adapter().gerarPix(new Dinheiro(new BigDecimal("50.00")), Instant.now()))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void mercadoPagoForaDoArVira503NoRest() {
        servidor.stop(0);

        assertThatThrownBy(() -> adapter().estornar("1")).isInstanceOf(ServicoIndisponivelException.class);
    }
}
