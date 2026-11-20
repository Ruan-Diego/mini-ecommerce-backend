package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.util.UUID;

/**
 * Reage aos eventos do pagamento-service. Cada chamada traz o `mensagemId` da mensagem: entregar a mesma
 * mensagem de novo (o RabbitMQ garante "pelo menos uma vez") não tem efeito na segunda vez.
 */
public interface ProcessarEventoDePagamentoUseCase {

    record PagamentoAprovado(UUID mensagemId, PedidoId pedido, ClienteId cliente, Dinheiro valor) {
    }

    record PagamentoEstornado(UUID mensagemId, PedidoId pedido) {
    }

    /** O pedido vai para PAGO, se o valor e o dono do pagamento conferirem com o pedido. */
    void aprovado(PagamentoAprovado evento);

    /** O pedido é cancelado, se o fluxo ainda permitir (antes de ser enviado). */
    void estornado(PagamentoEstornado evento);
}
