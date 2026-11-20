package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.adapters.out.gateway.MercadoPagoSandboxAdapter;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Webhook do Mercado Pago: assinatura, tradução do status e idempotência delegada ao use case. */
class MercadoPagoWebhookControllerTest {

    private static final String SEGREDO = "segredo-do-webhook-mp";

    private String transacaoProcessada;
    private Evento eventoProcessado;
    private Optional<Evento> eventoDoMercadoPago = Optional.of(Evento.APROVADO);
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        ProcessarEventoDoGatewayUseCase processar = (transacao, evento) -> {
            transacaoProcessada = transacao;
            eventoProcessado = evento;
            return ProcessarEventoDoGatewayUseCase.Resultado.PROCESSADO;
        };
        MercadoPagoSandboxAdapter mercadoPago = new MercadoPagoSandboxAdapter("http://127.0.0.1:1", "t", "p", "e@e.com", "") {
            @Override
            public Optional<Evento> consultarEvento(String transacaoId) {
                return eventoDoMercadoPago;
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new MercadoPagoWebhookController(processar, mercadoPago, SEGREDO))
                .setControllerAdvice(new TratadorDeErros()).build();
    }

    private static String assinar(String id, String requisicao, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(
                ("id:" + id + ";request-id:" + requisicao + ";ts:" + ts + ";").getBytes(StandardCharsets.UTF_8)));
        return "ts=" + ts + ",v1=" + v1;
    }

    @Test
    void assinaturaValidaProcessaOEventoDaConsulta() throws Exception {
        mvc.perform(post("/webhooks/gateway").param("data.id", "98765").param("type", "payment")
                        .header("x-request-id", "req-1").header("x-signature", assinar("98765", "req-1", "1700000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PROCESSADO"));

        assertThat(transacaoProcessada).isEqualTo("98765");
        assertThat(eventoProcessado).isEqualTo(Evento.APROVADO);
    }

    @Test
    void semAssinaturaOuComAssinaturaErradaDa401ENadaProcessa() throws Exception {
        mvc.perform(post("/webhooks/gateway").param("data.id", "98765").param("type", "payment"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/gateway").param("data.id", "98765").param("type", "payment")
                        .header("x-request-id", "req-1").header("x-signature", assinar("11111", "req-1", "1700000000")))
                .andExpect(status().isUnauthorized());

        assertThat(transacaoProcessada).isNull();
    }

    @Test
    void pagamentoPendenteEhIgnoradoSemErro() throws Exception {
        eventoDoMercadoPago = Optional.empty();

        mvc.perform(post("/webhooks/gateway").param("data.id", "98765").param("type", "payment")
                        .header("x-request-id", "req-1").header("x-signature", assinar("98765", "req-1", "1700000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("IGNORADO"));

        assertThat(transacaoProcessada).isNull();
    }

    @Test
    void tipoQueNaoEPagamentoEhIgnorado() throws Exception {
        mvc.perform(post("/webhooks/gateway").param("data.id", "98765").param("type", "merchant_order")
                        .header("x-request-id", "req-1").header("x-signature", assinar("98765", "req-1", "1700000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("IGNORADO"));

        assertThat(transacaoProcessada).isNull();
    }
}
