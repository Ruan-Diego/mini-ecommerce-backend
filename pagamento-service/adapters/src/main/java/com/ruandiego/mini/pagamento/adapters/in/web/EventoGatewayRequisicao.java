package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Corpo do webhook do gateway: qual transação mudou e para qual estado. */
record EventoGatewayRequisicao(@NotBlank String transacaoId, @NotNull Evento evento) {
}
