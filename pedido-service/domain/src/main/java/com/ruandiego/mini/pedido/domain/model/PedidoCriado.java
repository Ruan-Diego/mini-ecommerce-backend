package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * O pedido foi criado e aguarda pagamento. Só tipos simples (texto, número, data): o evento vira JSON
 * na outbox e é lido por outros serviços, que não conhecem os value objects deste domínio.
 */
public record PedidoCriado(String pedidoId, String clienteId, BigDecimal total, Instant ocorridoEm)
        implements DomainEvent {

    @Override
    public String tipo() {
        return "pedido.criado";
    }
}
