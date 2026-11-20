package com.ruandiego.mini.pagamento.application.port.out;

import com.ruandiego.mini.kernel.DomainEvent;

import java.util.List;

/**
 * Registra eventos de domínio para publicação (mesmo padrão do pedido-service). O adapter real NÃO fala com o RabbitMQ: grava na tabela
 * outbox, na mesma transação que salva o pagamento. Só o relay publica no broker, e só o que já foi
 * confirmado (commit). Assim nunca sai evento de um pagamento que acabou desfeito.
 */
public interface PublicadorEventos {

    void publicar(List<? extends DomainEvent> eventos);
}
