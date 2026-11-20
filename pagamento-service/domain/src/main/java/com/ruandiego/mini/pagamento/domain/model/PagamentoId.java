package com.ruandiego.mini.pagamento.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador do pagamento. */
public record PagamentoId(UUID valor) {

    public PagamentoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static PagamentoId novo() {
        return new PagamentoId(UUID.randomUUID());
    }

    public static PagamentoId de(String valor) {
        return new PagamentoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
