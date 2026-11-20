package com.ruandiego.mini.pagamento.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Referência ao pedido pago. Cada serviço tem o seu tipo: o pagamento só guarda o UUID, sem importar o pedido-service. */
public record PedidoId(UUID valor) {

    public PedidoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static PedidoId de(String valor) {
        return new PedidoId(UUID.fromString(valor));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
