package com.ruandiego.mini.estoque.adapters.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do `POST /admin/estoque/{produtoId}/ajustes`. `tipo` é ENTRADA, PERDA ou CORRECAO; `quantidade` é o que
 * entra ou sai (ENTRADA e PERDA, de 1 em diante) ou o saldo certo depois da contagem (CORRECAO, a partir de 0). A
 * regra de cada tipo é do domínio; aqui só se confere o formato.
 */
record AjusteComMotivoRequisicao(@NotBlank String tipo, @NotNull @Min(0) Integer quantidade) {
}
