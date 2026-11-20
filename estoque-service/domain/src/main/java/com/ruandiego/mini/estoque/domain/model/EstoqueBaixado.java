package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;

/** A baixa do estoque do pedido pago foi definitiva. Só tipos simples: o evento vira JSON na outbox. */
public record EstoqueBaixado(String pedidoId, Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "estoque.baixado";
    }
}
