package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.estoque.domain.RegraDeNegocioException;

import java.util.Objects;

/** Quanto de um produto uma reserva separou. */
public record ItemReservado(ProdutoId produtoId, int quantidade) {

    public ItemReservado {
        Objects.requireNonNull(produtoId, "produtoId");
        if (quantidade < 1) {
            throw new RegraDeNegocioException("a quantidade deve ser maior que zero");
        }
    }
}
