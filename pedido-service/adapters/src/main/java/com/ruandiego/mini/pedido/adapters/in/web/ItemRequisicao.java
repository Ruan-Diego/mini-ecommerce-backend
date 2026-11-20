package com.ruandiego.mini.pedido.adapters.in.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Item do pedido como o cliente o viu no carrinho; nome e preço de verdade vêm do catálogo. */
record ItemRequisicao(@NotNull UUID produtoId, int quantidade, @NotNull BigDecimal precoVisto) {
}
