package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.application.memoria.EventosDoGatewayProcessadosEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.GatewayPagamentoEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PagamentoRepositoryEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PagamentoController.class, WebhookController.class},
        properties = "pagamento.webhook.segredo=segredo-de-teste")
@Import(PagamentoEmMemoria.class)
class PagamentoControllerTest {

    private static final String SEGREDO = "segredo-de-teste";
    private final String pedidoId = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PagamentoRepositoryEmMemoria pagamentos;
    @Autowired
    private GatewayPagamentoEmMemoria gateway;
    @Autowired
    private EventosDoGatewayProcessadosEmMemoria processados;
    @Autowired
    private OutboxEmMemoria outbox;

    @BeforeEach
    void cenario() {
        pagamentos.limpar();
        processados.limpar();
        outbox.limpar();
        gateway.aprovarCartoes(true);
        gateway.ficarForaDoAr(false);
    }

    private static MockHttpServletRequestBuilder como(String cliente, MockHttpServletRequestBuilder req) {
        return req.principal(() -> cliente);
    }

    private String cartao(String numero) {
        return """
                {"pedidoId":"%s","valor":219.90,"metodo":"CARTAO","numeroCartao":"%s"}""".formatted(pedidoId, numero);
    }

    private String pix() {
        return """
                {"pedidoId":"%s","valor":219.90,"metodo":"PIX"}""".formatted(pedidoId);
    }

    private MvcResult iniciar(String cliente, String corpo) throws Exception {
        return mvc.perform(como(cliente, post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String campo(MvcResult resultado, String nome) throws Exception {
        return resultado.getResponse().getContentAsString().replaceAll(".*\"" + nome + "\":\"([^\"]+)\".*", "$1");
    }

    // ---- POST /pagamentos ----

    @Test
    void cartaoAprovadoVoltaCriadoComStatusAprovadoESemDadosDoCartao() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(cartao("4111111111111112")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/pagamentos/")))
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.metodo").value("CARTAO"))
                .andExpect(jsonPath("$.valor").value(219.90))
                .andExpect(jsonPath("$.pedidoId").value(pedidoId))
                .andExpect(jsonPath("$.pixCodigo").doesNotExist())
                .andExpect(content().string(not(containsString("4111"))));
    }

    @Test
    void cartaoRecusadoNaoEErroHttp() throws Exception {
        gateway.aprovarCartoes(false);

        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(cartao("4111111111111113")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECUSADO"))
                .andExpect(jsonPath("$.motivoRecusa").value("cartão recusado"));
    }

    @Test
    void pixVoltaPendenteComCodigoEExpiracao() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(pix()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pixCodigo").value(startsWith("PIX-tx-")))
                .andExpect(jsonPath("$.pixExpiraEm").value("2026-11-20T12:30:00Z"));
    }

    @Test
    void semIdentidadeEh401() throws Exception {
        mvc.perform(post("/pagamentos").contentType(MediaType.APPLICATION_JSON).content(pix()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void corpoSemMetodoEh400() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\":\"" + pedidoId + "\",\"valor\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartaoComNumeroInvalidoEh422SemEcoarONumero() throws Exception {
        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(cartao("1234")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("13 a 19")))
                .andExpect(content().string(not(containsString("1234"))));
    }

    @Test
    void pedidoJaPagoEh409() throws Exception {
        iniciar("ana", cartao("4111111111111112"));

        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(pix()))
                .andExpect(status().isConflict());
    }

    @Test
    void gatewayForaDoArEh503() throws Exception {
        gateway.ficarForaDoAr(true);

        mvc.perform(como("ana", post("/pagamentos")).contentType(MediaType.APPLICATION_JSON).content(pix()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void oNumeroDoCartaoNaoApareceNoToStringDaRequisicao() {
        PagamentoRequisicao requisicao = new PagamentoRequisicao(UUID.randomUUID(), java.math.BigDecimal.TEN,
                com.ruandiego.mini.pagamento.domain.model.MetodoPagamento.CARTAO, "4111111111111112");

        assertThat(requisicao.toString()).doesNotContain("4111111111111112").contains("***");
    }

    // ---- consultas ----

    @Test
    void consultaDoPagamentoEPorPedidoSoParaODono() throws Exception {
        String id = campo(iniciar("ana", cartao("4111111111111112")), "id");

        mvc.perform(como("ana", get("/pagamentos/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(como("beto", get("/pagamentos/" + id))).andExpect(status().isNotFound());
        mvc.perform(como("ana", get("/pagamentos/pedido/" + pedidoId))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(como("beto", get("/pagamentos/pedido/" + pedidoId))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(como("ana", get("/pagamentos/abc"))).andExpect(status().isBadRequest());
    }

    // ---- webhook ----

    private MockHttpServletRequestBuilder webhook(String segredo, String transacaoId, String evento) {
        MockHttpServletRequestBuilder req = post("/webhooks/gateway").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transacaoId\":\"%s\",\"evento\":\"%s\"}".formatted(transacaoId, evento));
        return segredo == null ? req : req.header("X-Webhook-Segredo", segredo);
    }

    @Test
    void webhookAprovaOPixEORepetidoNaoGeraEventoDuplicado() throws Exception {
        String transacao = campo(iniciar("ana", pix()), "transacaoId");

        mvc.perform(webhook(SEGREDO, transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PROCESSADO"));
        mvc.perform(webhook(SEGREDO, transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("JA_PROCESSADO"));
        mvc.perform(webhook(SEGREDO, transacao, "APROVADO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("JA_PROCESSADO"));

        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
        mvc.perform(como("ana", get("/pagamentos/pedido/" + pedidoId)))
                .andExpect(jsonPath("$[0].status").value("APROVADO"));
    }

    @Test
    void webhookSemOSegredoCertoEh401ENaoProcessaNada() throws Exception {
        String transacao = campo(iniciar("ana", pix()), "transacaoId");

        mvc.perform(webhook(null, transacao, "APROVADO")).andExpect(status().isUnauthorized());
        mvc.perform(webhook("segredo-errado", transacao, "APROVADO")).andExpect(status().isUnauthorized());

        assertThat(outbox.tiposGravados()).isEmpty();
        mvc.perform(como("ana", get("/pagamentos/pedido/" + pedidoId)))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    void webhookDeTransacaoDesconhecidaEh404EEventoInvalidoEh400() throws Exception {
        mvc.perform(webhook(SEGREDO, "tx-que-nao-existe", "APROVADO")).andExpect(status().isNotFound());
        mvc.perform(webhook(SEGREDO, "tx-1", "INVENTADO")).andExpect(status().isBadRequest());
    }

    @Test
    void webhookQueContrariaOFluxoEh422() throws Exception {
        String transacao = campo(iniciar("ana", cartao("4111111111111112")), "transacaoId");

        mvc.perform(webhook(SEGREDO, transacao, "RECUSADO")).andExpect(status().isUnprocessableEntity());
    }

    // ---- estorno ----

    @Test
    void estornaPagamentoAprovadoEUmSegundoEstornoEh422() throws Exception {
        String id = campo(iniciar("ana", cartao("4111111111111112")), "id");

        mvc.perform(post("/pagamentos/" + id + "/estorno")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));
        mvc.perform(post("/pagamentos/" + id + "/estorno")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/pagamentos/" + UUID.randomUUID() + "/estorno")).andExpect(status().isNotFound());

        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado", "pagamento.estornado");
    }
}
