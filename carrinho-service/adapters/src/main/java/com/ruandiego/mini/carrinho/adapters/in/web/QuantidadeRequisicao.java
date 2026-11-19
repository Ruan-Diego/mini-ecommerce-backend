package com.ruandiego.mini.carrinho.adapters.in.web;

import jakarta.validation.constraints.NotNull;

/** Corpo de PUT /carrinhos/atual/itens/{produtoId}. */
public record QuantidadeRequisicao(@NotNull Integer quantidade) {
}
