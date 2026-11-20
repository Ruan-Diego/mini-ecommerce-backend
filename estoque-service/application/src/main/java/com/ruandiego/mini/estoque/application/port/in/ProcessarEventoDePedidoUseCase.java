package com.ruandiego.mini.estoque.application.port.in;

import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;

import java.util.List;
import java.util.UUID;

/**
 * Reação do estoque aos eventos do pedido. `mensagemId` é o messageId da mensagem do broker: o use case o
 * registra para tratar cada mensagem uma só vez, mesmo que o broker a entregue de novo.
 */
public interface ProcessarEventoDePedidoUseCase {

    /** `pedido.criado`: separa os itens, ou publica `estoque.indisponivel` se algum não tem saldo. */
    void criado(PedidoCriado evento);

    /** `pedido.pago`: a baixa da reserva vira definitiva. */
    void pago(PedidoPago evento);

    /** `pedido.cancelado`: devolve o que estava reservado. */
    void cancelado(PedidoCancelado evento);

    record PedidoCriado(UUID mensagemId, PedidoId pedido, List<ItemReservado> itens) {
    }

    record PedidoPago(UUID mensagemId, PedidoId pedido) {
    }

    record PedidoCancelado(UUID mensagemId, PedidoId pedido) {
    }
}
