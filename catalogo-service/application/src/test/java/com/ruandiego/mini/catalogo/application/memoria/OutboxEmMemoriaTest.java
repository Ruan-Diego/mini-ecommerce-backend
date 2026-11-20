package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.contrato.OutboxContrato;
import com.ruandiego.mini.catalogo.application.port.out.Outbox;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;

/** A outbox em memória passa no mesmo contrato que a tabela outbox do JPA. */
class OutboxEmMemoriaTest extends OutboxContrato {

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
