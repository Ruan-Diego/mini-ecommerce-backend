package com.ruandiego.mini.pedido.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Referência a um produto do catálogo. Cada serviço tem o seu próprio tipo: o pedido não importa
 * código do catalogo-service, só guarda o UUID.
 */
public record ProdutoId(UUID valor) {

    public ProdutoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static ProdutoId de(String valor) {
        return new ProdutoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
