package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.List;

/** Admin: cadastra um produto novo, já ativo na vitrine. */
public interface CadastrarProdutoUseCase {

    /**
     * @throws com.ruandiego.mini.catalogo.application.ConflitoException se o SKU ou o slug já estiverem em uso
     * @throws com.ruandiego.mini.catalogo.application.NaoEncontradoException se a categoria não existir
     */
    ProdutoId cadastrar(Comando comando);

    /** slug nulo = gerado a partir do nome. */
    record Comando(Sku sku, String nome, Slug slug, String descricao, Dinheiro preco, List<Imagem> imagens,
                   CategoriaId categoriaId) {
    }
}
