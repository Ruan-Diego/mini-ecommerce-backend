package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.port.in.AdicionarImagemUseCase;
import com.ruandiego.mini.catalogo.application.port.out.ArmazenamentoImagens;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;

import java.util.Objects;

/**
 * Upload de imagens de produto. Tudo o que pode recusar o pedido é conferido ANTES de guardar o arquivo
 * (produto existe, galeria com vaga, texto alternativo), para não deixar arquivo órfão no armazenamento.
 * O limite de 5 continua sendo do agregado: o service só pergunta a ele.
 */
public class ImagensProdutoService implements AdicionarImagemUseCase {

    private final ProdutoRepository produtos;
    private final ArmazenamentoImagens armazenamento;

    public ImagensProdutoService(ProdutoRepository produtos, ArmazenamentoImagens armazenamento) {
        this.produtos = Objects.requireNonNull(produtos, "produtos");
        this.armazenamento = Objects.requireNonNull(armazenamento, "armazenamento");
    }

    @Override
    public Imagem adicionarImagem(ProdutoId id, ArquivoImagem arquivo, String textoAlternativo) {
        Produto produto = produtos.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("produto não encontrado: " + id));
        if (!produto.aceitaMaisImagens()) {
            throw new RegraDeNegocioException("produto aceita até " + Produto.IMAGENS_MAXIMO + " imagens");
        }
        if (textoAlternativo == null || textoAlternativo.isBlank()) {
            throw new RegraDeNegocioException("imagem precisa de texto alternativo");
        }

        Imagem imagem = new Imagem(armazenamento.guardar(arquivo), textoAlternativo);
        produto.adicionarImagem(imagem);
        produtos.salvar(produto);
        return imagem;
    }
}
