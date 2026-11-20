package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.memoria.CarrinhoClienteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoVisitanteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CupomRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarrinhoController.class)
@Import(CarrinhoEmMemoria.class)
class CarrinhoControllerTest {

    private static final ProdutoId ESFERA = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId CUBO = new ProdutoId(UUID.randomUUID());
    private static final String SESSAO = "sessao-1";
    private static final String CLIENTE = "ana";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CarrinhoVisitanteRepositoryEmMemoria visitantes;
    @Autowired
    private CarrinhoClienteRepositoryEmMemoria clientes;
    @Autowired
    private CupomRepositoryEmMemoria cupons;
    @Autowired
    private CarrinhoEmMemoria.CatalogoDeTeste catalogo;

    @BeforeEach
    void cenario() {
        visitantes.limpar();
        clientes.limpar();
        cupons.limpar();
        catalogo.limpar();
        catalogo.foraDoAr = false;
        catalogo.cadastrar(new CatalogoProdutos.Produto(ESFERA, "Esfera Chrome", Dinheiro.de("100.00")));
        catalogo.cadastrar(new CatalogoProdutos.Produto(CUBO, "Cubo Preto", Dinheiro.de("50.00")));
        cupons.adicionar(Cupom.percentual("DEZ", 10, CarrinhoEmMemoria.AGORA.plusSeconds(3600), null));
        cupons.adicionar(Cupom.percentual("VENCIDO", 10, CarrinhoEmMemoria.AGORA.minusSeconds(1), null));
    }

    private static MockHttpServletRequestBuilder comoVisitante(MockHttpServletRequestBuilder req) {
        return req.header("X-Sessao-Id", SESSAO);
    }

    private static MockHttpServletRequestBuilder comoCliente(MockHttpServletRequestBuilder req) {
        // O cliente é o Principal da requisição (na produção, o sub do JWT validado pelo bootstrap).
        return req.principal(() -> CLIENTE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String corpo) {
        return req.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private ResultActions adicionarComoVisitante(ProdutoId produto, int quantidade) throws Exception {
        return mvc.perform(json(comoVisitante(post("/carrinhos/atual/itens")),
                "{\"produtoId\":\"" + produto + "\",\"quantidade\":" + quantidade + "}"));
    }

    @Test
    void semIdentificacaoEhRequisicaoInvalida() throws Exception {
        mvc.perform(get("/carrinhos/atual"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("X-Sessao-Id")));
    }

    @Test
    void sessaoMalformadaEhRequisicaoInvalida() throws Exception {
        mvc.perform(get("/carrinhos/atual").header("X-Sessao-Id", "com espaço!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void visitanteSemCarrinhoRecebeUmVazio() throws Exception {
        mvc.perform(comoVisitante(get("/carrinhos/atual")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.quantidadeTotal").value(0))
                .andExpect(jsonPath("$.total").value(0.00))
                .andExpect(jsonPath("$.cupom").doesNotExist());
    }

    @Test
    void adicionaItemComNomeEPrecoDoCatalogo() throws Exception {
        adicionarComoVisitante(ESFERA, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].produtoId").value(ESFERA.toString()))
                .andExpect(jsonPath("$.itens[0].nome").value("Esfera Chrome"))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(100.00))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2))
                .andExpect(jsonPath("$.itens[0].subtotal").value(200.00))
                .andExpect(jsonPath("$.subtotal").value(200.00));

        mvc.perform(comoVisitante(get("/carrinhos/atual")))
                .andExpect(jsonPath("$.quantidadeTotal").value(2));
    }

    @Test
    void precoEnviadoPeloClienteHttpEhIgnorado() throws Exception {
        mvc.perform(json(comoVisitante(post("/carrinhos/atual/itens")),
                        "{\"produtoId\":\"" + ESFERA + "\",\"quantidade\":1,\"preco\":0.01,\"nome\":\"Grátis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Esfera Chrome"))
                .andExpect(jsonPath("$.subtotal").value(100.00));
    }

    @Test
    void produtoInexistenteEhNaoEncontrado() throws Exception {
        adicionarComoVisitante(new ProdutoId(UUID.randomUUID()), 1)
                .andExpect(status().isNotFound());
    }

    @Test
    void produtoIdQueNaoEUuidEhRequisicaoInvalida() throws Exception {
        mvc.perform(json(comoVisitante(post("/carrinhos/atual/itens")),
                        "{\"produtoId\":\"orbe\",\"quantidade\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quantidadeAusenteEhRequisicaoInvalida() throws Exception {
        mvc.perform(json(comoVisitante(post("/carrinhos/atual/itens")), "{\"produtoId\":\"" + ESFERA + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quantidadeForaDeUmADezEhRegraDeNegocio() throws Exception {
        adicionarComoVisitante(ESFERA, 11)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("entre 1 e 10")));
        adicionarComoVisitante(ESFERA, 0).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void catalogoForaDoArEServicoIndisponivel() throws Exception {
        catalogo.foraDoAr = true;

        adicionarComoVisitante(ESFERA, 1)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void alteraQuantidadeERemoveItem() throws Exception {
        adicionarComoVisitante(ESFERA, 1);
        adicionarComoVisitante(CUBO, 1);

        mvc.perform(json(comoVisitante(put("/carrinhos/atual/itens/" + ESFERA)), "{\"quantidade\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(450.00));

        mvc.perform(comoVisitante(delete("/carrinhos/atual/itens/" + CUBO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.subtotal").value(400.00));
    }

    @Test
    void alterarOuRemoverItemQueNaoEstaNoCarrinhoEhRegraDeNegocio() throws Exception {
        mvc.perform(json(comoVisitante(put("/carrinhos/atual/itens/" + ESFERA)), "{\"quantidade\":2}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(comoVisitante(delete("/carrinhos/atual/itens/" + ESFERA)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aplicaERemoveCupom() throws Exception {
        adicionarComoVisitante(ESFERA, 2);

        mvc.perform(json(comoVisitante(post("/carrinhos/atual/cupom")), "{\"codigo\":\"dez\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupom.codigo").value("DEZ"))
                .andExpect(jsonPath("$.cupom.tipo").value("PERCENTUAL"))
                .andExpect(jsonPath("$.desconto").value(20.00))
                .andExpect(jsonPath("$.total").value(180.00));

        mvc.perform(comoVisitante(delete("/carrinhos/atual/cupom")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupom").doesNotExist())
                .andExpect(jsonPath("$.total").value(200.00));
    }

    @Test
    void cupomInexistenteEhNaoEncontradoEVencidoEhRegraDeNegocio() throws Exception {
        adicionarComoVisitante(ESFERA, 1);

        mvc.perform(json(comoVisitante(post("/carrinhos/atual/cupom")), "{\"codigo\":\"NADA\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(json(comoVisitante(post("/carrinhos/atual/cupom")), "{\"codigo\":\"VENCIDO\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void checkoutExigeCliente() throws Exception {
        mvc.perform(comoVisitante(post("/carrinhos/atual/checkout")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void checkoutComCarrinhoVazioEhRegraDeNegocio() throws Exception {
        mvc.perform(comoCliente(post("/carrinhos/atual/checkout")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("vazio")));
    }

    @Test
    void checkoutDevolveAFotoFinal() throws Exception {
        mvc.perform(json(comoCliente(post("/carrinhos/atual/itens")),
                "{\"produtoId\":\"" + ESFERA + "\",\"quantidade\":2}"));
        mvc.perform(json(comoCliente(post("/carrinhos/atual/cupom")), "{\"codigo\":\"DEZ\"}"));

        mvc.perform(comoCliente(post("/carrinhos/atual/checkout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(180.00))
                .andExpect(jsonPath("$.itens.length()").value(1));
    }

    @Test
    void itensDeVisitanteContinuamNoCarrinhoDepoisDoLogin() throws Exception {
        adicionarComoVisitante(ESFERA, 3);

        // Primeiro acesso depois do login: o front manda cliente e a sessão do tempo de visitante.
        mvc.perform(comoCliente(comoVisitante(get("/carrinhos/atual"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].produtoId").value(ESFERA.toString()))
                .andExpect(jsonPath("$.quantidadeTotal").value(3));

        // Depois, só o cliente: o carrinho continua lá e o do visitante foi consumido.
        mvc.perform(comoCliente(get("/carrinhos/atual")))
                .andExpect(jsonPath("$.quantidadeTotal").value(3));
        mvc.perform(comoVisitante(get("/carrinhos/atual")))
                .andExpect(jsonPath("$.quantidadeTotal").value(0));
    }
}
