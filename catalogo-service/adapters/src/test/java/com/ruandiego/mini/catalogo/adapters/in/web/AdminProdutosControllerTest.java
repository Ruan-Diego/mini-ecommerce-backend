package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.memoria.CategoriaRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.PublicadorEventosEmMemoria;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProdutosController.class)
@Import(CatalogoEmMemoria.class)
class AdminProdutosControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepositoryEmMemoria produtos;

    @Autowired
    private CategoriaRepositoryEmMemoria categorias;

    @Autowired
    private PublicadorEventosEmMemoria publicador;

    private Categoria esferas;

    @BeforeEach
    void cenario() {
        produtos.limpar();
        categorias.limpar();
        publicador.limpar();
        esferas = Categoria.criar("Esferas", new Slug("esferas"), null);
        categorias.salvar(esferas);
    }

    @Test
    void cadastraEDevolveLocationEId() throws Exception {
        String id = cadastrar("ESF-CHROME", "Esfera Chrome", "289.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/admin/produtos/")))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        Produto salvo = produtos.buscarPorSlug(new Slug("esfera-chrome")).orElseThrow();
        assertThat(salvo.id().toString()).isEqualTo(id);
        assertThat(salvo.ativo()).isTrue();
    }

    @Test
    void listaTambemOsDesativadosEBuscaPeloSku() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "289.00").andExpect(status().isCreated());
        cadastrar("ESF-LIME", "Esfera Lime", "199.00").andExpect(status().isCreated());
        Produto lime = produtos.buscarPorSlug(new Slug("esfera-lime")).orElseThrow();
        mvc.perform(delete("/admin/produtos/{id}", lime.id())).andExpect(status().isNoContent());

        mvc.perform(get("/admin/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens[0].nome").value("Esfera Chrome"))
                .andExpect(jsonPath("$.itens[0].ativo").value(true))
                .andExpect(jsonPath("$.itens[1].nome").value("Esfera Lime"))
                .andExpect(jsonPath("$.itens[1].ativo").value(false));

        mvc.perform(get("/admin/produtos").param("busca", "esf-lime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].sku").value("ESF-LIME"));
    }

    @Test
    void listaComTamanhoInvalidoEh422() throws Exception {
        mvc.perform(get("/admin/produtos").param("tamanho", "0")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void skuRepetidoEh409() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "289.00").andExpect(status().isCreated());

        cadastrar("ESF-CHROME", "Outra Esfera", "100.00")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.campo").value("sku"));
    }

    @Test
    void precoZeroEh422() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void skuAusenteEh422() throws Exception {
        corpo(post("/admin/produtos"), """
                {"nome": "Esfera", "preco": 10, "categoriaId": "%s"}""".formatted(esferas.id()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("SKU")));
    }

    @Test
    void campoObrigatorioFaltandoEh400() throws Exception {
        corpo(post("/admin/produtos"), """
                {"sku": "ESF-1", "preco": 10, "categoriaId": "%s"}""".formatted(esferas.id()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void jsonMalFormadoEh400() throws Exception {
        corpo(post("/admin/produtos"), "{\"sku\": ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void categoriaInexistenteEh404() throws Exception {
        corpo(post("/admin/produtos"), """
                {"sku": "ESF-1", "nome": "Esfera", "preco": 10, "categoriaId": "%s"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriaIdQueNaoEhUuidEh400() throws Exception {
        corpo(post("/admin/produtos"), """
                {"sku": "ESF-1", "nome": "Esfera", "preco": 10, "categoriaId": "esferas"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("categoriaId")));
    }

    @Test
    void atualizaPrecoEPublicaPrecoAlterado() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "289.00");
        Produto produto = produtos.buscarPorSlug(new Slug("esfera-chrome")).orElseThrow();

        corpo(put("/admin/produtos/" + produto.id()), """
                {"nome": "Esfera Chrome", "preco": 249.90, "categoriaId": "%s",
                 "imagens": [{"url": "https://img.exemplo.com/a.jpg", "textoAlternativo": "Esfera"}]}"""
                .formatted(esferas.id()))
                .andExpect(status().isNoContent());

        Produto atualizado = produtos.buscarPorId(produto.id()).orElseThrow();
        assertThat(atualizado.preco()).isEqualTo(Dinheiro.de("249.90"));
        assertThat(atualizado.imagens()).hasSize(1);
        assertThat(publicador.publicados()).hasAtLeastOneElementOfType(PrecoAlterado.class);
    }

    @Test
    void imagemSemTextoAlternativoEh422() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "289.00");
        Produto produto = produtos.buscarPorSlug(new Slug("esfera-chrome")).orElseThrow();

        corpo(put("/admin/produtos/" + produto.id()), """
                {"nome": "Esfera Chrome", "preco": 289, "categoriaId": "%s",
                 "imagens": [{"url": "https://img.exemplo.com/a.jpg", "textoAlternativo": ""}]}"""
                .formatted(esferas.id()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void atualizarComIdInvalidoEh400() throws Exception {
        corpo(put("/admin/produtos/123"), """
                {"nome": "Esfera", "preco": 10, "categoriaId": "%s"}""".formatted(esferas.id()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desativaSemApagar() throws Exception {
        cadastrar("ESF-CHROME", "Esfera Chrome", "289.00");
        Produto produto = produtos.buscarPorSlug(new Slug("esfera-chrome")).orElseThrow();

        mvc.perform(delete("/admin/produtos/" + produto.id())).andExpect(status().isNoContent());

        assertThat(produtos.buscarPorId(produto.id()).orElseThrow().ativo()).isFalse();
    }

    @Test
    void desativarInexistenteEh404() throws Exception {
        mvc.perform(delete("/admin/produtos/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions cadastrar(String sku, String nome, String preco) throws Exception {
        return corpo(post("/admin/produtos"), """
                {"sku": "%s", "nome": "%s", "preco": %s, "categoriaId": "%s"}"""
                .formatted(sku, nome, preco, esferas.id()));
    }

    private ResultActions corpo(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao, String json)
            throws Exception {
        return mvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
