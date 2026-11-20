package com.ruandiego.mini.catalogo;

import com.ruandiego.mini.catalogo.application.service.RelayDoOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dispara o relay da outbox a cada segundo (`catalogo.outbox.intervalo`, em ms). O agendamento é detalhe
 * de framework, por isso fica no bootstrap: o RelayDoOutbox só sabe "publicar o que está pendente".
 * Desligue com `catalogo.outbox.relay-ligado=false` (os testes de integração chamam o relay à mão).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxAgendador {

    private static final Logger log = LoggerFactory.getLogger(OutboxAgendador.class);
    private static final int LOTE = 100;

    private final RelayDoOutbox relay;
    private final boolean ligado;

    OutboxAgendador(RelayDoOutbox relay, @Value("${catalogo.outbox.relay-ligado:true}") boolean ligado) {
        this.relay = relay;
        this.ligado = ligado;
    }

    @Scheduled(fixedDelayString = "${catalogo.outbox.intervalo:1000}")
    void publicarPendentes() {
        if (!ligado) {
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
