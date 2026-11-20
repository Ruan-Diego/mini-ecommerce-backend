package com.ruandiego.mini.estoque.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador do pedido (do pedido-service). A reserva é identificada por ele: um pedido, no máximo uma reserva. */
public record PedidoId(UUID valor) {

    public PedidoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static PedidoId de(String valor) {
        return new PedidoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
