package com.ruandiego.mini.catalogo.adapters.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Corpo do POST e do PUT de /admin/produtos.
 *
 * A Bean Validation aqui só garante o formato da requisição (campo presente). As regras de negócio
 * (preço > 0, SKU bem formado, imagem com texto alternativo) continuam no domínio: assim valem também
 * para quem chamar o use case por outro adapter, e o controller não duplica regra.
 * `sku` e `slug` só são lidos no POST; no PUT são ignorados (não mudam depois do cadastro).
 */
public record ProdutoRequisicao(String sku, @NotBlank String nome, String slug, String descricao,
                                @NotNull BigDecimal preco, List<@Valid ImagemJson> imagens,
                                @NotBlank String categoriaId) {

    public ProdutoRequisicao {
        imagens = imagens == null ? List.of() : imagens;
        descricao = descricao == null ? "" : descricao;
    }
}
