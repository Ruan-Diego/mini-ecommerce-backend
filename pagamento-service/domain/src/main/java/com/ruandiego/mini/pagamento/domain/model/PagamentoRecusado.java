package com.ruandiego.mini.pagamento.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;

/** Pagamento recusado (pelo gateway) ou expirado (Pix não pago em 30 minutos); `motivo` diz qual. */
public record PagamentoRecusado(String pagamentoId, String pedidoId, String clienteId, BigDecimal valor,
                                String transacaoId, String motivo, Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "pagamento.recusado";
    }
}
