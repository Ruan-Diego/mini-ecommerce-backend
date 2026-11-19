package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.memoria.CategoriaRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VitrineController.class)
@Import(CatalogoEmMemoria.class)
class VitrineControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepositoryEmMemoria produtos;

    @Autowired
    private CategoriaRepositoryEmMemoria categorias;

    private Categoria esferas;
    private Categoria pecas;

    @BeforeEach
    void cenario() {
        produtos.limpar();
        categorias.limpar();
        esferas = Categoria.criar("Esferas", new Slug("esferas"), null);
        pecas = Categoria.criar("Peças", new Slug("pecas"), null);
        categorias.salvar(esferas);
        categorias.salvar(pecas);
        produtos.salvar(produto("ESF-CHROME", "Esfera Chrome", "289.00", esferas));
        produtos.salvar(produto("ESF-OPAL", "Esfera Opala", "149.90", esferas));
        produtos.salvar(produto("PEC-BASE", "Base de Latão", "59.00", pecas));
    }

    @Test
    void buscaPaginadaEOrdenadaPorMenorPreco() throws Exception {
        mvc.perform(get("/produtos").param("ordem", "menor-preco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].slug", contains("base-de-latao", "esfera-opala", "esfera-chrome")))
                .andExpect(jsonPath("$.itens[0].preco").value(59.00))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(24))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void filtraPorCategoriaEFaixaDePreco() throws Exception {
        mvc.perform(get("/produtos").param("categoria", "esferas").param("precoMax", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[*].nome", contains("Esfera Opala")));
    }

    @Test
    void paginaComTamanhoInformado() throws Exception {
        mvc.perform(get("/produtos").param("tamanho", "2").param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void aceitaDisponivelSemFiltrarAteAFase4() throws Exception {
        mvc.perform(get("/produtos").param("disponivel", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void ordemDesconhecidaEh400() throws Exception {
        mvc.perform(get("/produtos").param("ordem", "aleatoria"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("ordem")));
    }

    @Test
    void precoMinimoMaiorQueMaximoEh422() throws Exception {
        mvc.perform(get("/produtos").param("precoMin", "300").param("precoMax", "100"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Regra de negócio violada"));
    }

    @Test
    void precoQueNaoEhNumeroEh400() throws Exception {
        mvc.perform(get("/produtos").param("precoMin", "barato"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void detalhePeloSlug() throws Exception {
        mvc.perform(get("/produtos/esfera-chrome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("ESF-CHROME"))
                .andExpect(jsonPath("$.categoriaId").value(esferas.id().toString()))
                .andExpect(jsonPath("$.imagens[0].textoAlternativo").value("Foto de Esfera Chrome"));
    }

    @Test
    void produtoInexistenteEh404() throws Exception {
        mvc.perform(get("/produtos/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Não encontrado"));
    }

    @Test
    void produtoInativoEh404() throws Exception {
        Produto chrome = produtos.buscarPorSlug(new Slug("esfera-chrome")).orElseThrow();
        chrome.desativar();
        produtos.salvar(chrome);

        mvc.perform(get("/produtos/esfera-chrome")).andExpect(status().isNotFound());
    }

    @Test
    void listaCategoriasEmOrdemAlfabetica() throws Exception {
        mvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", contains("esferas", "pecas")))
                .andExpect(jsonPath("$[0].paiId").doesNotExist());
    }

    private static Produto produto(String sku, String nome, String preco, Categoria categoria) {
        return Produto.criar(new Sku(sku), nome, Slug.gerarDe(nome), "", Dinheiro.de(preco),
                List.of(new Imagem("https://img.exemplo.com/" + sku + ".jpg", "Foto de " + nome)), categoria.id());
    }
}
