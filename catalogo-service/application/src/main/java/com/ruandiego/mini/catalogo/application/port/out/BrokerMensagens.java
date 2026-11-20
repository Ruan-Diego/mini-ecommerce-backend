package com.ruandiego.mini.catalogo.application.port.out;

import java.util.UUID;

/**
 * Port de saída para o broker (RabbitMQ). `tipo` é a routing key; `payload` já é o JSON do evento.
 * `id` é o id da linha da outbox e vira o `messageId` da mensagem: o consumidor o usa para ser
 * idempotente. `traceId` (pode ser nulo) liga o log de quem publica ao de quem consome.
 */
public interface BrokerMensagens {

    /** @throws RuntimeException se o broker não confirmou o recebimento: a mensagem fica pendente. */
    void publicar(UUID id, String tipo, String payload, String traceId);
}
