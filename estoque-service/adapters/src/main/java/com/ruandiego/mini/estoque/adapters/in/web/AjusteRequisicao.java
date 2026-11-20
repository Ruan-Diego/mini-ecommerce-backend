package com.ruandiego.mini.estoque.adapters.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpo do `PUT /admin/estoque/{produtoId}`: o novo saldo vendável. */
record AjusteRequisicao(@NotNull @Min(0) Integer disponivel) {
}
