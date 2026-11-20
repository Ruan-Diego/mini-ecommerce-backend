package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.domain.model.ClienteId;

/**
 * Reação ao evento PedidoCriado do pedido-service: o carrinho do cliente foi transformado em pedido, então
 * é esvaziado (itens e cupom). Idempotente por natureza: esvaziar um carrinho já vazio, ou que não existe,
 * não muda nada, então a reentrega da mensagem é inofensiva.
 */
public interface EsvaziarCarrinhoDoClienteUseCase {
    void esvaziar(ClienteId cliente);
}
