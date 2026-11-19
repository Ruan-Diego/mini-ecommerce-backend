package com.ruandiego.mini.carrinho.adapters.in.web;

import jakarta.validation.constraints.NotBlank;

/** Corpo de POST /carrinhos/atual/cupom. */
public record CupomRequisicao(@NotBlank String codigo) {
}
