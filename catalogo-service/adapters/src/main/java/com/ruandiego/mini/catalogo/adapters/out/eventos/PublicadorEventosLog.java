package com.ruandiego.mini.catalogo.adapters.out.eventos;

import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.kernel.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter provisório do PublicadorEventos: só registra os eventos no log.
 * Na Fase 3 ele é trocado pelo adapter de outbox (tabela na mesma transação + envio ao RabbitMQ).
 * Os use cases não mudam nessa troca: só conhecem o port.
 */
@Component
public class PublicadorEventosLog implements PublicadorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(PublicadorEventosLog.class);

    @Override
    public void publicar(List<DomainEvent> eventos) {
        eventos.forEach(evento -> LOG.info("evento de domínio (ainda sem outbox): {}", evento));
    }
}
