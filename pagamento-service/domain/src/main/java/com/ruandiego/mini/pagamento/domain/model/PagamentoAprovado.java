package com.ruandiego.mini.pagamento.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;

/** Pagamento aprovado. Só tipos simples: o evento vira JSON na outbox e é lido por outros serviços. */
public record PagamentoAprovado(String pagamentoId, String pedidoId, String clienteId, BigDecimal valor,
                          String transacaoId, Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "pagamento.aprovado";
    }
}
