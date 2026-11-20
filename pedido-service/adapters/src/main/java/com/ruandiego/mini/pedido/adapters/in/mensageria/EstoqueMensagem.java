package com.ruandiego.mini.pedido.adapters.in.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato JSON do evento `estoque.indisponivel` (publicado pelo estoque-service). O pedido só precisa do id
 * do pedido; a lista de produtos sem estoque e a data são ignoradas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EstoqueMensagem(String pedidoId) {
}
