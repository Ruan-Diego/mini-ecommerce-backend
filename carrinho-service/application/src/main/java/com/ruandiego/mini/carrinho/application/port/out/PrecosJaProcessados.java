package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

import java.time.Instant;

/**
 * Port de saída que lembra, por produto, o instante do último PrecoAlterado já aplicado. É o que torna o
 * consumo idempotente (a mesma mensagem duas vezes não faz nada) e imune à ordem (uma mensagem velha que
 * chega depois de uma nova é descartada). Dado durável: sobrevive a reinício do serviço.
 */
public interface PrecosJaProcessados {

    /** O evento deste instante já foi aplicado, ou um mais novo já foi? */
    boolean jaProcessado(ProdutoId produtoId, Instant ocorridoEm);

    /** Guarda o instante, se for mais novo que o já guardado (nunca volta no tempo). */
    void registrar(ProdutoId produtoId, Instant ocorridoEm);
}
