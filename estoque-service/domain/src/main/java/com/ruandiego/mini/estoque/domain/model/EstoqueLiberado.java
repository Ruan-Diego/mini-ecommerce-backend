package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;

/** A reserva do pedido cancelado foi desfeita e o estoque voltou. Só tipos simples: o evento vira JSON na outbox. */
public record EstoqueLiberado(String pedidoId, Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "estoque.liberado";
    }
}
