package com.ruandiego.mini.pedido.adapters.in.web;

import jakarta.validation.constraints.NotBlank;

/** Endereço de entrega. Regras finas (CEP com 8 dígitos, UF com 2 letras) são do domínio. */
record EnderecoRequisicao(@NotBlank String destinatario, @NotBlank String cep, @NotBlank String logradouro,
                          @NotBlank String numero, String complemento, @NotBlank String bairro,
                          @NotBlank String cidade, @NotBlank String uf) {
}
