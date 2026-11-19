package com.ruandiego.mini.catalogo.application.port.out;

import com.ruandiego.mini.kernel.DomainEvent;

import java.util.List;

/**
 * Port de saída dos eventos de domínio.
 *
 * O adapter real (Fase 3) grava os eventos na tabela de outbox na mesma transação do agregado;
 * um processo separado envia ao RabbitMQ depois do commit. Assim, nunca sai evento de algo que
 * não foi salvo, nem fica salvo algo cujo evento se perdeu.
 */
public interface PublicadorEventos {

    void publicar(List<DomainEvent> eventos);
}
