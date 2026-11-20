package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corpo do POST /pagamentos. `numeroCartao` só vale para CARTAO e só vai até o gateway.
 * O toString omite o número de propósito: um log de requisição nunca pode vazar o cartão.
 */
record PagamentoRequisicao(@NotNull UUID pedidoId, @NotNull BigDecimal valor, @NotNull MetodoPagamento metodo,
                           String numeroCartao) {

    @Override
    public String toString() {
        return "PagamentoRequisicao[pedidoId=" + pedidoId + ", valor=" + valor + ", metodo=" + metodo
                + ", numeroCartao=" + (numeroCartao == null ? "null" : "***") + "]";
    }
}
