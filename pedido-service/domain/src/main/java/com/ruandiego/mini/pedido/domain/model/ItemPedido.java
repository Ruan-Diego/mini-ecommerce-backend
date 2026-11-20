package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;

import java.util.Objects;

/** Foto do item no momento da compra: nome e preço ficam congelados, mesmo que o catálogo mude depois. */
public record ItemPedido(ProdutoId produtoId, String nome, Dinheiro precoUnitario, int quantidade) {

    public static final int QUANTIDADE_MAXIMA = 10;

    public ItemPedido {
        Objects.requireNonNull(produtoId, "produtoId");
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("nome do item é obrigatório");
        }
        Objects.requireNonNull(precoUnitario, "precoUnitario");
        if (!precoUnitario.ehPositivo()) {
            throw new RegraDeNegocioException("preço do item deve ser maior que zero");
        }
        if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
            throw new RegraDeNegocioException("quantidade deve estar entre 1 e " + QUANTIDADE_MAXIMA);
        }
    }

    public Dinheiro subtotal() {
        return precoUnitario.multiplicar(quantidade);
    }
}
