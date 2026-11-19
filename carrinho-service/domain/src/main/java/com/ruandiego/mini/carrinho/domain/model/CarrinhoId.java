package com.ruandiego.mini.carrinho.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidade de um Carrinho. */
public record CarrinhoId(UUID valor) {

    public CarrinhoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static CarrinhoId novo() {
        return new CarrinhoId(UUID.randomUUID());
    }

    public static CarrinhoId de(String valor) {
        return new CarrinhoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
