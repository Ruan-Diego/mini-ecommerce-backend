package com.ruandiego.mini.catalogo.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidade de uma Categoria. */
public record CategoriaId(UUID valor) {

    public CategoriaId {
        Objects.requireNonNull(valor, "valor");
    }

    public static CategoriaId novo() {
        return new CategoriaId(UUID.randomUUID());
    }

    public static CategoriaId de(String valor) {
        return new CategoriaId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
