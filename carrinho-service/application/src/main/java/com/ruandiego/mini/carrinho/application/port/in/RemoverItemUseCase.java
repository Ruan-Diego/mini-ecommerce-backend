package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

/** Tira um item do carrinho. */
public interface RemoverItemUseCase {

    /** @throws com.ruandiego.mini.carrinho.domain.RegraDeNegocioException se o item não está no carrinho */
    CarrinhoResumo remover(Identidade identidade, ProdutoId produtoId);
}
