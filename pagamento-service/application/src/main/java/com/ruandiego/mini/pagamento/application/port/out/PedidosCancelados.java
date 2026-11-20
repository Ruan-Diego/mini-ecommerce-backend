package com.ruandiego.mini.pagamento.application.port.out;

import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.time.Instant;

/**
 * Pedidos que o pedido-service cancelou (por pagamento recusado, estoque indisponível, expiração ou pedido do
 * cliente), conhecidos aqui pelo evento `pedido.cancelado`. Existe para uma regra de dinheiro: não se cobra um
 * pedido cancelado. O pagamento-service não consulta o pedido-service; guarda só o que o evento contou.
 */
public interface PedidosCancelados {

    /** Registra o cancelamento. Registrar de novo o mesmo pedido não tem efeito. */
    void registrar(PedidoId pedido, Instant quando);

    boolean estaCancelado(PedidoId pedido);
}
