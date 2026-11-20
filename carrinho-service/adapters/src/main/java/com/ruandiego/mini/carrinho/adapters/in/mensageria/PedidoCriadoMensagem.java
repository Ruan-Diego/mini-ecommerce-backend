package com.ruandiego.mini.carrinho.adapters.in.mensageria;

/**
 * Contrato JSON da mensagem `pedido.criado` publicada pelo pedido-service (o evento PedidoCriado serializado
 * pela outbox). O carrinho só lê o que precisa; campos novos do publicador (total, ocorridoEm...) são ignorados.
 */
record PedidoCriadoMensagem(String pedidoId, String clienteId) {
}
