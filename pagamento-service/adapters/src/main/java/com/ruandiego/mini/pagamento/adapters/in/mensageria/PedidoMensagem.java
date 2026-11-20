package com.ruandiego.mini.pagamento.adapters.in.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato JSON do evento `pedido.cancelado` (publicado pelo pedido-service). O pagamento só precisa do id
 * do pedido; cliente, status anterior e data são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PedidoMensagem(String pedidoId) {
}
