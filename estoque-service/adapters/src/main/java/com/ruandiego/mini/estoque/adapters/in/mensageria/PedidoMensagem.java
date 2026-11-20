package com.ruandiego.mini.estoque.adapters.in.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Contrato JSON dos eventos `pedido.criado`, `pedido.pago` e `pedido.cancelado` (publicados pelo pedido-service).
 * Só o que o estoque usa; o resto (cliente, total, status anterior, data) é ignorado. `itens` só vem no
 * `pedido.criado`: é o par produto e quantidade que a reserva precisa.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PedidoMensagem(String pedidoId, List<Item> itens) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String produtoId, int quantidade) {
    }
}
