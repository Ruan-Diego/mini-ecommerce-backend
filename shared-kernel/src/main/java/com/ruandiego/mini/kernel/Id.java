package com.ruandiego.mini.kernel;

import java.util.Objects;
import java.util.UUID;

/** Identificador baseado em UUID. Cada agregado usa o seu próprio tipo de Id. */
public record Id(UUID valor) {

    public Id {
        Objects.requireNonNull(valor, "valor");
    }

    public static Id novo() {
        return new Id(UUID.randomUUID());
    }

    public static Id de(String valor) {
        return new Id(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
