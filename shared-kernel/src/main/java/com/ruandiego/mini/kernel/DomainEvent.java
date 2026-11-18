package com.ruandiego.mini.kernel;

import java.time.Instant;

/** Fato de negócio que já aconteceu. Publicado depois do commit, via outbox. */
public interface DomainEvent {

    /** Nome estável usado como routing key no RabbitMQ, ex.: "catalogo.preco-alterado". */
    String tipo();

    Instant ocorridoEm();
}
