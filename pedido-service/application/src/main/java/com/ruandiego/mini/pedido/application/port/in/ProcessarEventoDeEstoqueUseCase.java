package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.util.UUID;

/**
 * Reage aos eventos do estoque-service. Cada chamada traz o `mensagemId` da mensagem: entregar a mesma
 * mensagem de novo (o RabbitMQ garante "pelo menos uma vez") não tem efeito na segunda vez.
 */
public interface ProcessarEventoDeEstoqueUseCase {

    record EstoqueIndisponivel(UUID mensagemId, PedidoId pedido) {
    }

    /**
     * Passo de compensação da saga: não havia estoque para o pedido, então ele é cancelado. O cancelamento
     * gera `pedido.cancelado`, que o pagamento usa para estornar caso o cliente já tenha pago.
     */
    void indisponivel(EstoqueIndisponivel evento);
}
