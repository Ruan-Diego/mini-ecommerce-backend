package com.ruandiego.mini.pedido.adapters.in.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Contrato JSON dos eventos `pagamento.aprovado` e `pagamento.estornado` (publicados pelo pagamento-service).
 * Só os campos que o pedido usa; o resto (id do pagamento, transação, data) é ignorado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PagamentoMensagem(String pedidoId, String clienteId, BigDecimal valor) {
}
