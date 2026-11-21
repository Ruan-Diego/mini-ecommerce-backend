package com.ruandiego.mini.pedido.adapters.in.web;

import jakarta.validation.constraints.NotBlank;

/** Corpo do PATCH de status. `codigoRastreio` só vale (e é obrigatório) para ENVIADO; a regra é do domínio. */
record AlterarStatusRequisicao(@NotBlank String status, String codigoRastreio) {
}
