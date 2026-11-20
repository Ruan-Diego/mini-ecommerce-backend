package com.ruandiego.mini.pagamento.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados do pagamento:
 *
 * PENDENTE → APROVADO | RECUSADO | EXPIRADO (expirar vale só para Pix), e APROVADO → ESTORNADO.
 * RECUSADO, EXPIRADO e ESTORNADO são finais. Pagamento recusado não volta: o cliente tenta de novo com outro pagamento.
 */
public enum StatusPagamento {
    PENDENTE,
    APROVADO,
    RECUSADO,
    EXPIRADO,
    ESTORNADO;

    public Set<StatusPagamento> proximosPermitidos() {
        return switch (this) {
            case PENDENTE -> EnumSet.of(APROVADO, RECUSADO, EXPIRADO);
            case APROVADO -> EnumSet.of(ESTORNADO);
            case RECUSADO, EXPIRADO, ESTORNADO -> EnumSet.noneOf(StatusPagamento.class);
        };
    }

    public boolean podeIrPara(StatusPagamento destino) {
        return proximosPermitidos().contains(destino);
    }
}
