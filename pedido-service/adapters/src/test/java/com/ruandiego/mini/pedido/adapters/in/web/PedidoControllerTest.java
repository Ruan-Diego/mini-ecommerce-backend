package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PedidoController.class, FreteController.class})
@Import(PedidoEmMemoria.class)
class PedidoControllerTest {

    private static final ProdutoId TECLADO = new ProdutoId(UUID.randomUUID());

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PedidoRepositoryEmMemoria pedidos;
    @Autowired
    private OutboxEmMemoria outbox;
    @Autowired
    private PedidoEmMemoria.CatalogoDeTeste catalogo;

    @BeforeEach
    void cenario() {
        pedidos.limpar();
        outbox.limpar();
        catalogo.limpar();
        catalogo.foraDoAr = false;
        catalogo.cadastrar(new CatalogoConsulta.Produto(TECLADO, "Teclado", Dinheiro.de("200.00")));
    }

    private static MockHttpServletRequestBuilder como(String cliente, MockHttpServletRequestBuilder req) {
        return req.principal(() -> cliente);
    }

    private static String corpo(String produtoId, String precoVisto, String cep, String frete) {
        return """
                {"itens":[{"produtoId":"%s","quantidade":2,"precoVisto":%s}],
                 "endereco":{"destinatario":"Ana Souza","cep":"%s","logradouro":"Av. Paulista","numero":"1000",
                             "bairro":"Bela Vista","cidade":"São Paulo","uf":"SP"},
                 "freteCodigo":"%s"}""".formatted(produtoId, precoVisto, cep, frete);
    }

    private String criarComoAna() throws Exception {
        MvcResult resultado = mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "200.00", "01310-100", "padrao")))
                .andExpect(status().isCreated()).andReturn();
        String corpoResposta = resultado.getResponse().getContentAsString();
        return corpoResposta.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    // ---- POST /pedidos ----

    @Test
    void criaOPedidoDoClienteLogadoComPrecoDoCatalogo() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "200.00", "01310-100", "padrao")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/pedidos/")))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.itens[0].nome").value("Teclado"))
                .andExpect(jsonPath("$.itens[0].subtotal").value(400.00))
                .andExpect(jsonPath("$.frete.codigo").value("padrao"))
                .andExpect(jsonPath("$.subtotal").value(400.00))
                // 400,00 de itens passa de 299,00: o frete padrão é grátis.
                .andExpect(jsonPath("$.frete.valor").value(0.0))
                .andExpect(jsonPath("$.total").value(400.00))
                .andExpect(jsonPath("$.endereco.cep").value("01310100"));
    }

    @Test
    void semIdentidadeEh401() throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "200.00", "01310100", "padrao")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/pedidos/meus")).andExpect(status().isUnauthorized());
    }

    @Test
    void corpoSemItensEh400() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"itens":[],"endereco":{"destinatario":"A","cep":"01310100","logradouro":"R","numero":"1",
                         "bairro":"B","cidade":"C","uf":"SP"},"freteCodigo":"padrao"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cepInvalidoEh422() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "200.00", "123", "padrao")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("CEP")));
    }

    @Test
    void precoDesatualizadoEh409() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "150.00", "01310100", "padrao")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Teclado")));
    }

    @Test
    void produtoInexistenteEh404() throws Exception {
        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), "10.00", "01310100", "padrao")))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogoForaDoArEh503() throws Exception {
        catalogo.foraDoAr = true;

        mvc.perform(como("ana", post("/pedidos")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(TECLADO.toString(), "200.00", "01310100", "padrao")))
                .andExpect(status().isServiceUnavailable());
    }

    // ---- GET ----

    @Test
    void listaSoOsPedidosDoClienteLogado() throws Exception {
        criarComoAna();

        mvc.perform(como("ana", get("/pedidos/meus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(como("beto", get("/pedidos/meus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void obterPedidoDeOutroClienteEh404() throws Exception {
        String id = criarComoAna();

        mvc.perform(como("ana", get("/pedidos/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(como("beto", get("/pedidos/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void idQueNaoEUuidEh400() throws Exception {
        mvc.perform(como("ana", get("/pedidos/abc"))).andExpect(status().isBadRequest());
    }

    // ---- cancelar ----

    @Test
    void cancelaOPedidoEUmSegundoCancelamentoEh422() throws Exception {
        String id = criarComoAna();

        mvc.perform(como("ana", post("/pedidos/" + id + "/cancelar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        mvc.perform(como("ana", post("/pedidos/" + id + "/cancelar")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("CANCELADO")));
    }

    @Test
    void cancelarPedidoDeOutroClienteEh404() throws Exception {
        String id = criarComoAna();

        mvc.perform(como("beto", post("/pedidos/" + id + "/cancelar"))).andExpect(status().isNotFound());
    }

    // ---- frete ----

    @Test
    void cotaFreteDeUmCepEUmSubtotal() throws Exception {
        mvc.perform(get("/fretes/cotacao").param("cep", "01310-100").param("subtotal", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].codigo").value("padrao"))
                .andExpect(jsonPath("$[0].valor").value(19.90))
                .andExpect(jsonPath("$[1].prazoDias").value(3));
    }

    @Test
    void cotacaoComCepInvalidoEh422ESemParametroEh400() throws Exception {
        mvc.perform(get("/fretes/cotacao").param("cep", "1").param("subtotal", "100"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/fretes/cotacao").param("cep", "01310100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/fretes/cotacao").param("cep", "01310100").param("subtotal", "-1"))
                .andExpect(status().isBadRequest());
    }
}
