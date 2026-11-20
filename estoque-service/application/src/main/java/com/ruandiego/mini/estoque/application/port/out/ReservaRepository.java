package com.ruandiego.mini.estoque.application.port.out;

import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.Reserva;

import java.util.Optional;

/** Port de saída: persistência das reservas, uma por pedido. */
public interface ReservaRepository {

    Optional<Reserva> buscarPorPedido(PedidoId pedido);

    /** Como {@link #buscarPorPedido}, mas trava a reserva até o fim da transação (pago e cancelado do mesmo pedido não se atropelam). */
    Optional<Reserva> buscarPorPedidoComTrava(PedidoId pedido);

    void salvar(Reserva reserva);
}
