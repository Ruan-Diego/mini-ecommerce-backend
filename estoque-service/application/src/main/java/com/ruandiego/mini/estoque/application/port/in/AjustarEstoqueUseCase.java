package com.ruandiego.mini.estoque.application.port.in;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;

public interface AjustarEstoqueUseCase {

    /** Define o saldo vendável do produto (cria o item se ainda não existe). O que está reservado não muda. */
    ItemEstoque definirDisponivel(ProdutoId produto, int disponivel);
}
