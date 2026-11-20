package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.pagamento.application.contrato.EventosDoGatewayProcessadosContrato;
import com.ruandiego.mini.pagamento.application.contrato.MensagensProcessadasContrato;
import com.ruandiego.mini.pagamento.application.contrato.OutboxContrato;
import com.ruandiego.mini.pagamento.application.contrato.PedidosCanceladosContrato;
import com.ruandiego.mini.pagamento.application.contrato.PagamentoRepositoryContrato;
import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;
import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.pagamento.application.port.out.Outbox;
import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;

/** Os adapters em memória passam nos mesmos contratos que os adapters JPA reais. */
class EmMemoriaPassaNosContratosTest {

    static class Pagamentos extends PagamentoRepositoryContrato {
        private final PagamentoRepositoryEmMemoria repositorio = new PagamentoRepositoryEmMemoria();

        @Override
        protected PagamentoRepository pagamentos() {
            return repositorio;
        }
    }

    static class Processados extends EventosDoGatewayProcessadosContrato {
        private final EventosDoGatewayProcessadosEmMemoria registro = new EventosDoGatewayProcessadosEmMemoria();

        @Override
        protected EventosDoGatewayProcessados processados() {
            return registro;
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

    static class MensagensTratadas extends MensagensProcessadasContrato {
        private final MensagensProcessadasEmMemoria registro = new MensagensProcessadasEmMemoria();

        @Override
        protected MensagensProcessadas processadas() {
            return registro;
        }
    }

    static class Cancelados extends PedidosCanceladosContrato {
        private final PedidosCanceladosEmMemoria registro = new PedidosCanceladosEmMemoria();

        @Override
        protected PedidosCancelados cancelados() {
            return registro;
        }
    }
}
