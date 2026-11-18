package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.List;

/** Admin: atualiza dados e preço. Mudança de preço publica PrecoAlterado. SKU e slug não mudam aqui. */
public interface AtualizarProdutoUseCase {

    /**
     * @throws com.ruandiego.mini.catalogo.application.NaoEncontradoException se o produto ou a categoria não existirem
     */
    void atualizar(Comando comando);

    record Comando(ProdutoId id, String nome, String descricao, Dinheiro preco, List<Imagem> imagens,
                   CategoriaId categoriaId) {
    }
}
