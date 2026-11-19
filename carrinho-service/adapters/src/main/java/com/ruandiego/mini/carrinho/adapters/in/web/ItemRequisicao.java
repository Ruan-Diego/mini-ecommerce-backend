package com.ruandiego.mini.carrinho.adapters.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de POST /carrinhos/atual/itens. Só o produto e a quantidade: nome e preço vêm do catálogo.
 * A faixa da quantidade (1 a 10) é regra do domínio, e não validação do JSON.
 */
public record ItemRequisicao(@NotBlank String produtoId, @NotNull Integer quantidade) {
}
