package com.ruandiego.mini.pedido.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados do pedido. Cada status conhece os próximos permitidos, então a regra do fluxo
 * mora num só lugar:
 *
 * AGUARDANDO_PAGAMENTO → PAGO → EM_SEPARACAO → ENVIADO → ENTREGUE
 * e CANCELADO a partir de AGUARDANDO_PAGAMENTO, PAGO ou EM_SEPARACAO (depois de enviado, não cancela).
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGO,
    EM_SEPARACAO,
    ENVIADO,
    ENTREGUE,
    CANCELADO;

    public Set<StatusPedido> proximosPermitidos() {
        return switch (this) {
            case AGUARDANDO_PAGAMENTO -> EnumSet.of(PAGO, CANCELADO);
            case PAGO -> EnumSet.of(EM_SEPARACAO, CANCELADO);
            case EM_SEPARACAO -> EnumSet.of(ENVIADO, CANCELADO);
            case ENVIADO -> EnumSet.of(ENTREGUE);
            case ENTREGUE, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeIrPara(StatusPedido destino) {
        return proximosPermitidos().contains(destino);
    }

    public boolean terminal() {
        return proximosPermitidos().isEmpty();
    }
}
