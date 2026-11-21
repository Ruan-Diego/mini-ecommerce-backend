package com.ruandiego.mini.estoque.domain.model;

/**
 * O motivo de um ajuste manual do saldo vendável. ENTRADA soma e PERDA subtrai a quantidade (relativos: valem
 * sobre o saldo de agora, mesmo com pedidos reservando ao mesmo tempo); CORREÇÃO define o saldo certo (absoluto,
 * depois de uma contagem).
 */
public enum TipoDeAjuste {
    ENTRADA, PERDA, CORRECAO
}
