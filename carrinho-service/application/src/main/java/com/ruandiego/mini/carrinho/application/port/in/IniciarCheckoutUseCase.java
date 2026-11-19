package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;

/**
 * Confere o carrinho do cliente logado antes de virar pedido e devolve a foto final (itens, desconto, total).
 * Criar o pedido em si é do pedido-service (Fase 3): aqui só se garante que o carrinho está em ordem.
 */
public interface IniciarCheckoutUseCase {

    /** @throws com.ruandiego.mini.carrinho.domain.RegraDeNegocioException se o carrinho está vazio ou o cupom deixou de valer */
    CarrinhoResumo iniciar(ClienteId cliente);
}
