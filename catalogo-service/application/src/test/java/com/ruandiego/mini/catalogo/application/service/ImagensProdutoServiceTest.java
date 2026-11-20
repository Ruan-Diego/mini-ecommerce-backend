package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.memoria.ArmazenamentoImagensEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upload sobre os adapters em memória: sem Spring, sem disco, sem mocks. */
class ImagensProdutoServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final ProdutoRepositoryEmMemoria produtos = new ProdutoRepositoryEmMemoria();
    private final ArmazenamentoImagensEmMemoria armazenamento = new ArmazenamentoImagensEmMemoria();
    private final ImagensProdutoService service = new ImagensProdutoService(produtos, armazenamento);

    private Produto produtoCom(int imagens) {
        List<Imagem> lista = new ArrayList<>();
        for (int i = 0; i < imagens; i++) {
            lista.add(new Imagem("https://cdn.mini.dev/" + i + ".png", "Foto " + i));
        }
        Produto produto = Produto.criar(new Sku("ESF-" + imagens), "Esfera", null, "", Dinheiro.de("10.00"), lista,
                CategoriaId.novo());
        produtos.salvar(produto);
        return produto;
    }

    @Test
    void guardaOArquivoEAcrescentaAImagemNoFimDaGaleria() {
        Produto produto = produtoCom(1);

        Imagem imagem = service.adicionarImagem(produto.id(), ArquivoImagem.de(PNG), "  Esfera de lado  ");

        assertThat(imagem.url()).startsWith(ArmazenamentoImagensEmMemoria.BASE).endsWith(".png");
        assertThat(imagem.textoAlternativo()).isEqualTo("Esfera de lado");
        assertThat(armazenamento.guardados()).containsKey(imagem.url());
        assertThat(produtos.buscarPorId(produto.id()).orElseThrow().imagens()).hasSize(2).last().isEqualTo(imagem);
    }

    @Test
    void galeriaCheiaRecusaSemGuardarOArquivo() {
        Produto produto = produtoCom(5);

        assertThatThrownBy(() -> service.adicionarImagem(produto.id(), ArquivoImagem.de(PNG), "Sexta"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("5");
        assertThat(armazenamento.guardados()).isEmpty();
    }

    @Test
    void semTextoAlternativoRecusaSemGuardarOArquivo() {
        Produto produto = produtoCom(0);

        assertThatThrownBy(() -> service.adicionarImagem(produto.id(), ArquivoImagem.de(PNG), " "))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(armazenamento.guardados()).isEmpty();
    }

    @Test
    void produtoInexistenteEhNaoEncontrado() {
        assertThatThrownBy(() -> service.adicionarImagem(ProdutoId.novo(), ArquivoImagem.de(PNG), "Foto"))
                .isInstanceOf(NaoEncontradoException.class);
        assertThat(armazenamento.guardados()).isEmpty();
    }
}
