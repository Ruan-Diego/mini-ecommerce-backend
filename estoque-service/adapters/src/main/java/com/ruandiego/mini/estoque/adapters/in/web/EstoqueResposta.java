package com.ruandiego.mini.estoque.adapters.in.web;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;

/** Saldo do produto na API. `emEstoque` é o que a vitrine precisa saber: dá para vender pelo menos um? */
record EstoqueResposta(String produtoId, int disponivel, int reservado, boolean emEstoque) {

    static EstoqueResposta de(ItemEstoque item) {
        return new EstoqueResposta(item.produtoId().toString(), item.disponivel(), item.reservado(),
                item.disponivel() > 0);
    }
}
