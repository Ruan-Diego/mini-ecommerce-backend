package com.ruandiego.mini.catalogo.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidade de um Produto. Tipo próprio para o compilador não deixar trocar por um CategoriaId. */
public record ProdutoId(UUID valor) {

    public ProdutoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static ProdutoId novo() {
        return new ProdutoId(UUID.randomUUID());
    }

    public static ProdutoId de(String valor) {
        return new ProdutoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
