package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;

/** O estoque do pedido foi separado. Só tipos simples: o evento vira JSON na outbox. */
public record EstoqueReservado(String pedidoId, Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "estoque.reservado";
    }
}
