package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

/** Define a quantidade (1 a 10) de um item que já está no carrinho. */
public interface AlterarQuantidadeUseCase {

    /** @throws com.ruandiego.mini.carrinho.domain.RegraDeNegocioException se o item não está no carrinho ou a quantidade é inválida */
    CarrinhoResumo alterar(Identidade identidade, ProdutoId produtoId, int quantidade);
}
