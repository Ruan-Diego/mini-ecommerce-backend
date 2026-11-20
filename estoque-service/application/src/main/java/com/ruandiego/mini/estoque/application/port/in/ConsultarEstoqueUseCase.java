package com.ruandiego.mini.estoque.application.port.in;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;

public interface ConsultarEstoqueUseCase {

    /** @throws com.ruandiego.mini.estoque.application.NaoEncontradoException se o produto não tem estoque cadastrado */
    ItemEstoque consultar(ProdutoId produto);
}
