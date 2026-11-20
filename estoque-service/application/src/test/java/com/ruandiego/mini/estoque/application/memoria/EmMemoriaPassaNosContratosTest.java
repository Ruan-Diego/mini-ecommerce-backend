package com.ruandiego.mini.estoque.application.memoria;

import com.ruandiego.mini.estoque.application.contrato.ItemEstoqueRepositoryContrato;
import com.ruandiego.mini.estoque.application.contrato.MensagensProcessadasContrato;
import com.ruandiego.mini.estoque.application.contrato.OutboxContrato;
import com.ruandiego.mini.estoque.application.contrato.ReservaRepositoryContrato;
import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.estoque.application.port.out.Outbox;
import com.ruandiego.mini.estoque.application.port.out.PublicadorEventos;
import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;

/** Os adapters em memória passam nos mesmos contratos que os adapters JPA reais. */
class EmMemoriaPassaNosContratosTest {

    static class Itens extends ItemEstoqueRepositoryContrato {
        private final ItemEstoqueRepositoryEmMemoria repositorio = new ItemEstoqueRepositoryEmMemoria();

        @Override
        protected ItemEstoqueRepository itens() {
            return repositorio;
        }
    }

    static class Reservas extends ReservaRepositoryContrato {
        private final ReservaRepositoryEmMemoria repositorio = new ReservaRepositoryEmMemoria();

        @Override
        protected ReservaRepository reservas() {
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

    static class Processadas extends MensagensProcessadasContrato {
        private final MensagensProcessadasEmMemoria registro = new MensagensProcessadasEmMemoria();

        @Override
        protected MensagensProcessadas processadas() {
            return registro;
        }
    }
}
