package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Adapter em memória: um conjunto de pedidos cancelados. */
public class PedidosCanceladosEmMemoria implements PedidosCancelados {

    private final Set<PedidoId> pedidos = new HashSet<>();

    @Override
    public void registrar(PedidoId pedido, Instant quando) {
        pedidos.add(pedido);
    }

    @Override
    public boolean estaCancelado(PedidoId pedido) {
        return pedidos.contains(pedido);
    }

    public void limpar() {
        pedidos.clear();
    }
}
