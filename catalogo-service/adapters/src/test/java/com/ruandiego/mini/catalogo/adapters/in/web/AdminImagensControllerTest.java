package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.memoria.ArmazenamentoImagensEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload pelo HTTP sobre o service real e os adapters em memória (o papel admin é provado no bootstrap). */
@WebMvcTest(AdminProdutosController.class)
@Import(CatalogoEmMemoria.class)
class AdminImagensControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProdutoRepositoryEmMemoria produtos;
    @Autowired
    private ArmazenamentoImagensEmMemoria armazenamento;

    @BeforeEach
    void limpar() {
        produtos.limpar();
        armazenamento.limpar();
    }

    private Produto produtoCom(int imagens) {
        List<Imagem> lista = new ArrayList<>();
        for (int i = 0; i < imagens; i++) {
            lista.add(new Imagem("https://cdn.mini.dev/" + i + ".png", "Foto " + i));
        }
        Produto produto = Produto.criar(new Sku("ESF-01"), "Esfera", null, "", Dinheiro.de("10.00"), lista,
                CategoriaId.novo());
        produtos.salvar(produto);
        return produto;
    }

    private static MockMultipartHttpServletRequestBuilder upload(Object id, byte[] conteudo) {
        MockMultipartHttpServletRequestBuilder req = multipart("/admin/produtos/{id}/imagens", id);
        req.file(new MockMultipartFile("arquivo", "foto.png", "image/png", conteudo));
        return req;
    }

    @Test
    void enviaAImagemEDevolve201ComAUrl() throws Exception {
        Produto produto = produtoCom(0);

        mvc.perform(upload(produto.id(), PNG).param("textoAlternativo", "Esfera de frente"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith(ArmazenamentoImagensEmMemoria.BASE)))
                .andExpect(jsonPath("$.textoAlternativo").value("Esfera de frente"));

        assertThat(produtos.buscarPorId(produto.id()).orElseThrow().imagens()).hasSize(1);
        assertThat(armazenamento.guardados()).hasSize(1);
    }

    @Test
    void sextaImagemEh422() throws Exception {
        Produto produto = produtoCom(5);

        mvc.perform(upload(produto.id(), PNG).param("textoAlternativo", "Sexta"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("5")));
        assertThat(armazenamento.guardados()).isEmpty();
    }

    @Test
    void arquivoQueNaoEhImagemEh422MesmoDeclarandoImagePng() throws Exception {
        Produto produto = produtoCom(0);
        byte[] svg = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);

        mvc.perform(upload(produto.id(), svg).param("textoAlternativo", "Falsa"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("JPEG, PNG ou WebP")));
    }

    @Test
    void faltandoArquivoOuTextoAlternativoEh400() throws Exception {
        Produto produto = produtoCom(0);

        mvc.perform(multipart("/admin/produtos/{id}/imagens", produto.id()).param("textoAlternativo", "Sem arquivo"))
                .andExpect(status().isBadRequest());
        mvc.perform(upload(produto.id(), PNG)).andExpect(status().isBadRequest());
    }

    @Test
    void produtoInexistenteEh404EIdMalformadoEh400() throws Exception {
        mvc.perform(upload(UUID.randomUUID(), PNG).param("textoAlternativo", "X")).andExpect(status().isNotFound());
        mvc.perform(upload("nao-e-uuid", PNG).param("textoAlternativo", "X")).andExpect(status().isBadRequest());
    }
}
