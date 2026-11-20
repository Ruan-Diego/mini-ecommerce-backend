package com.ruandiego.mini.pagamento.application.port.in;

import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.util.UUID;

/**
 * Reage aos eventos do pedido-service. Cada chamada traz o `mensagemId` da mensagem: entregar a mesma
 * mensagem de novo (o RabbitMQ garante "pelo menos uma vez") não tem efeito na segunda vez.
 */
public interface ProcessarEventoDePedidoUseCase {

    record PedidoCancelado(UUID mensagemId, PedidoId pedido) {
    }

    /**
     * Passo de compensação da saga: o pedido foi cancelado. Se o cliente já tinha pago, o pagamento aprovado é
     * estornado (no gateway e no domínio) e `pagamento.estornado` é publicado. Sem pagamento aprovado, nada a fazer.
     */
    void cancelado(PedidoCancelado evento);
}
