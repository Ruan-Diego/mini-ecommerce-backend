package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;

/** O pedido mudou de status. `tipo` é "pedido." + status em minúsculas com hífen (ex.: "pedido.em-separacao"). */
public record PedidoStatusAlterado(String pedidoId, String clienteId, String de, String para, Instant ocorridoEm)
        implements DomainEvent {

    @Override
    public String tipo() {
        return "pedido." + para.toLowerCase().replace('_', '-');
    }
}
