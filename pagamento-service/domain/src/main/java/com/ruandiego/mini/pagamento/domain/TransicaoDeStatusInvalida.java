package com.ruandiego.mini.pagamento.domain;

import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;

/** Tentativa de levar o pagamento para um status que o fluxo não permite (ex.: estornar um pagamento recusado). */
public class TransicaoDeStatusInvalida extends RegraDeNegocioException {

    private final StatusPagamento de;
    private final StatusPagamento para;

    public TransicaoDeStatusInvalida(StatusPagamento de, StatusPagamento para) {
        super("transição inválida: " + de + " → " + para);
        this.de = de;
        this.para = para;
    }

    public StatusPagamento de() {
        return de;
    }

    public StatusPagamento para() {
        return para;
    }
}
