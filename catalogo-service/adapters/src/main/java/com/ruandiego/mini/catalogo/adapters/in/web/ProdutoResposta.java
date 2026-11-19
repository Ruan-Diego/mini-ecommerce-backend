package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.domain.model.Produto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Formato JSON do produto. O agregado nunca vai direto para o JSON: assim o domínio pode mudar
 * (ou ganhar campos internos) sem quebrar o contrato da API.
 */
public record ProdutoResposta(String id, String sku, String nome, String slug, String descricao, BigDecimal preco,
                              List<ImagemJson> imagens, String categoriaId, boolean ativo) {

    static ProdutoResposta de(Produto produto) {
        return new ProdutoResposta(produto.id().toString(), produto.sku().valor(), produto.nome(),
                produto.slug().valor(), produto.descricao(), produto.preco().valor(),
                produto.imagens().stream().map(ImagemJson::de).toList(), produto.categoriaId().toString(),
                produto.ativo());
    }
}
