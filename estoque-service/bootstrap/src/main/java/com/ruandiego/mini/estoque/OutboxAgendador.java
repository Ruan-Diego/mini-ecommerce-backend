package com.ruandiego.mini.estoque;

import com.ruandiego.mini.estoque.application.service.RelayDoOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Agenda o relay da outbox, a cada segundo (`estoque.outbox.intervalo`, em ms). É detalhe de framework, por
 * isso fica no bootstrap. Desliga por propriedade (`estoque.outbox.relay-ligado=false`): os testes de
 * integração o desligam e chamam o relay à mão, para controlar o momento.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxAgendador {

    private static final Logger log = LoggerFactory.getLogger(OutboxAgendador.class);
    private static final int LOTE = 100;

    private final RelayDoOutbox relay;
    private final boolean relayLigado;

    OutboxAgendador(RelayDoOutbox relay, @Value("${estoque.outbox.relay-ligado:true}") boolean relayLigado) {
        this.relay = relay;
        this.relayLigado = relayLigado;
    }

    @Scheduled(fixedDelayString = "${estoque.outbox.intervalo:1000}")
    void publicarPendentes() {
        if (!relayLigado) {
            return;
        }
        try {
            int publicadas = relay.publicarPendentes(LOTE);
            if (publicadas > 0) {
                log.info("outbox: {} evento(s) publicado(s)", publicadas);
            }
        } catch (RuntimeException e) {
            // Banco fora do ar, por exemplo: a próxima rodada tenta de novo.
            log.warn("outbox: falha ao ler as mensagens pendentes: {}", e.getMessage());
        }
    }
}
