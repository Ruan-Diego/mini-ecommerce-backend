package com.ruandiego.mini.pedido.adapters.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Corpo do POST /pedidos. */
record PedidoRequisicao(@NotEmpty List<@Valid @NotNull ItemRequisicao> itens,
                        @NotNull @Valid EnderecoRequisicao endereco,
                        @NotBlank String freteCodigo) {
}
