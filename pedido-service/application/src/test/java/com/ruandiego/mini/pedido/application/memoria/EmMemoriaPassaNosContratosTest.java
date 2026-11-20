package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.pedido.application.contrato.OutboxContrato;
import com.ruandiego.mini.pedido.application.contrato.PedidoRepositoryContrato;
import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;

/** Os adapters em memória passam nos mesmos contratos que os adapters JPA reais. */
class EmMemoriaPassaNosContratosTest {

    static class Pedidos extends PedidoRepositoryContrato {
        private final PedidoRepositoryEmMemoria repositorio = new PedidoRepositoryEmMemoria();

        @Override
        protected PedidoRepository pedidos() {
            return repositorio;
        }
    }

    static class OutboxEmMemoriaContrato extends OutboxContrato {
        private final OutboxEmMemoria outbox = new OutboxEmMemoria();

        @Override
        protected PublicadorEventos publicador() {
            return outbox;
        }

        @Override
        protected Outbox outbox() {
            return outbox;
        }
    }
}
