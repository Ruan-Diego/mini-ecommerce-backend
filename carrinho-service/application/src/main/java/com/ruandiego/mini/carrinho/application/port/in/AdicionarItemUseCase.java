package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

/**
 * Põe um produto no carrinho. Nome e preço vêm do catálogo, nunca do cliente HTTP:
 * quem chama só diz qual produto e quantas unidades.
 */
public interface AdicionarItemUseCase {

    /**
     * @throws com.ruandiego.mini.carrinho.application.NaoEncontradoException se o produto não existe ou está inativo
     * @throws com.ruandiego.mini.carrinho.domain.RegraDeNegocioException se a quantidade passar de 10
     */
    CarrinhoResumo adicionar(Identidade identidade, ProdutoId produtoId, int quantidade);
}
