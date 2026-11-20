package com.ruandiego.mini.pedido.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador do pedido. */
public record PedidoId(UUID valor) {

    public PedidoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static PedidoId novo() {
        return new PedidoId(UUID.randomUUID());
    }

    public static PedidoId de(String valor) {
        return new PedidoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
