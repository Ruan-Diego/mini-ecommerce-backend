package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O adapter REST do admin sobre o service real e os adapters em memória. A exigência do papel admin é da
 * segurança do bootstrap e é provada no PedidoIntegracaoTest; aqui fica a tradução HTTP ↔ use case.
 */
@WebMvcTest(AdminPedidosController.class)
@Import(PedidoEmMemoria.class)
class AdminPedidosControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PedidoRepositoryEmMemoria pedidos;
    @Autowired
    private OutboxEmMemoria outbox;

    @BeforeEach
    void limpar() {
        pedidos.limpar();
        outbox.limpar();
    }

    /** 2 × 50,00 + 10,00 de frete = 110,00. */
    private Pedido pedido(String cliente, Instant criadoEm, boolean pago) {
        Pedido pedido = Pedido.criar(new ClienteId(cliente),
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Teclado", Dinheiro.de("50.00"), 2)),
                new EnderecoEntrega("Ana", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("10.00"), 5), criadoEm);
        if (pago) {
            pedido.marcarComoPago(criadoEm);
        }
        pedido.limparEventos();
        pedidos.salvar(pedido);
        return pedido;
    }

    private static String corpoStatus(String status, String rastreio) {
        return rastreio == null ? "{\"status\":\"%s\"}".formatted(status)
                : "{\"status\":\"%s\",\"codigoRastreio\":\"%s\"}".formatted(status, rastreio);
    }

    // ---- indicadores ----

    @Test
    void devolveOsIndicadoresDoDiaDaLoja() throws Exception {
        // PedidoEmMemoria.AGORA = 20/11 12:00 UTC = 09:00 em Brasília.
        pedido("ana", PedidoEmMemoria.AGORA.minusSeconds(3600), true);
        pedido("beto", PedidoEmMemoria.AGORA.minusSeconds(60), false);
        pedido("ana", Instant.parse("2026-11-19T12:00:00Z"), false);

        mvc.perform(get("/admin/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dia").value("2026-11-20"))
                .andExpect(jsonPath("$.pedidosHoje").value(2))
                .andExpect(jsonPath("$.faturamentoDoDia").value(110.00))
                .andExpect(jsonPath("$.aguardandoPagamento").value(2));
    }

    // ---- GET /admin/pedidos ----

    @Test
    void listaPedidosDeTodosOsClientesComFiltroDeStatusEPeriodo() throws Exception {
        Pedido pagoHoje = pedido("ana", PedidoEmMemoria.AGORA, true);
        pedido("beto", PedidoEmMemoria.AGORA, false);
        pedido("ana", Instant.parse("2026-11-10T12:00:00Z"), true);

        mvc.perform(get("/admin/pedidos").param("status", "PAGO").param("de", "2026-11-20").param("ate", "2026-11-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id").value(pagoHoje.id().toString()))
                .andExpect(jsonPath("$.itens[0].clienteId").value("ana"))
                .andExpect(jsonPath("$.itens[0].total").value(110.00))
                .andExpect(jsonPath("$.itens[0].transicoesPermitidas", contains("EM_SEPARACAO", "CANCELADO")));

        mvc.perform(get("/admin/pedidos").param("status", "PAGO,AGUARDANDO_PAGAMENTO").param("tamanho", "2"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.itens", hasSize(2)));
    }

    @Test
    void filtrosMalformadosSao400ELimitesSao422() throws Exception {
        mvc.perform(get("/admin/pedidos").param("status", "VOANDO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/admin/pedidos").param("de", "20/11/2026")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/pedidos").param("tamanho", "500")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/admin/pedidos").param("de", "2026-11-20").param("ate", "2026-11-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void obtemQualquerPedidoPeloId() throws Exception {
        Pedido pedido = pedido("beto", PedidoEmMemoria.AGORA, false);

        mvc.perform(get("/admin/pedidos/{id}", pedido.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value("beto"))
                .andExpect(jsonPath("$.transicoesPermitidas", contains("CANCELADO")));
        mvc.perform(get("/admin/pedidos/{id}", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/pedidos/nao-e-uuid")).andExpect(status().isBadRequest());
    }

    // ---- PATCH /admin/pedidos/{id}/status ----

    @Test
    void alteraOStatusComRastreioNoEnvio() throws Exception {
        Pedido pedido = pedido("ana", PedidoEmMemoria.AGORA, true);
        String url = "/admin/pedidos/" + pedido.id() + "/status";

        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(corpoStatus("EM_SEPARACAO", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_SEPARACAO"))
                .andExpect(jsonPath("$.transicoesPermitidas", contains("ENVIADO", "CANCELADO")));
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(corpoStatus("enviado", "BR123456789")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"))
                .andExpect(jsonPath("$.codigoRastreio").value("BR123456789"));

        assertThat(outbox.tiposGravados()).containsExactly("pedido.em-separacao", "pedido.enviado");
    }

    @Test
    void transicaoInvalidaEnvioSemRastreioEPagoManualSao422() throws Exception {
        Pedido aguardando = pedido("ana", PedidoEmMemoria.AGORA, false);
        String url = "/admin/pedidos/" + aguardando.id() + "/status";

        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(corpoStatus("ENTREGUE", null)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(corpoStatus("PAGO", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("pagamento")));

        Pedido emSeparacao = pedido("ana", PedidoEmMemoria.AGORA, true);
        mvc.perform(patch("/admin/pedidos/" + emSeparacao.id() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(corpoStatus("EM_SEPARACAO", null))).andExpect(status().isOk());
        mvc.perform(patch("/admin/pedidos/" + emSeparacao.id() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoStatus("ENVIADO", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("rastreio")));
    }

    @Test
    void statusDesconhecidoOuAusenteNoCorpoEh400() throws Exception {
        Pedido pedido = pedido("ana", PedidoEmMemoria.AGORA, true);
        String url = "/admin/pedidos/" + pedido.id() + "/status";

        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(corpoStatus("SUMIDO", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
