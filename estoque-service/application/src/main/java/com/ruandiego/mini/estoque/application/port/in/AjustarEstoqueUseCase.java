package com.ruandiego.mini.estoque.application.port.in;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import com.ruandiego.mini.estoque.domain.model.TipoDeAjuste;

public interface AjustarEstoqueUseCase {

    /** Define o saldo vendável do produto (cria o item se ainda não existe). O que está reservado não muda. */
    ItemEstoque definirDisponivel(ProdutoId produto, int disponivel);

    /**
     * Ajuste com motivo: ENTRADA soma `quantidade` ao saldo de agora, PERDA subtrai (não pode passar do disponível) e
     * CORRECAO define o saldo para `quantidade` (pode ser 0). Entrada e perda são relativas e feitas sob a trava do
     * item, então não se perdem pedidos que reservam ao mesmo tempo. Registra `estoque.ajustado` (motivo, antes e
     * depois) na outbox. Cria o item se ainda não existe.
     */
    ItemEstoque ajustar(ProdutoId produto, TipoDeAjuste tipo, int quantidade);
}
