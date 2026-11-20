package com.ruandiego.mini.pagamento;

import com.ruandiego.mini.pagamento.application.port.in.ExpirarPixVencidosUseCase;
import com.ruandiego.mini.pagamento.application.service.RelayDoOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Os dois agendamentos do serviço, ambos detalhe de framework (por isso no bootstrap):
 * - o relay da outbox, a cada segundo (`pagamento.outbox.intervalo`, em ms);
 * - a expiração dos Pix vencidos, a cada minuto (`pagamento.pix.intervalo-de-expiracao`, em ms).
 * Cada um se desliga por propriedade (`...relay-ligado=false`, `...expiracao-ligada=false`); os testes de
 * integração os desligam e chamam o relay e o use case à mão, para controlar o momento.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxAgendador {

    private static final Logger log = LoggerFactory.getLogger(OutboxAgendador.class);
    private static final int LOTE = 100;

    private final RelayDoOutbox relay;
    private final ExpirarPixVencidosUseCase expirar;
    private final boolean relayLigado;
    private final boolean expiracaoLigada;

    OutboxAgendador(RelayDoOutbox relay, ExpirarPixVencidosUseCase expirar,
                    @Value("${pagamento.outbox.relay-ligado:true}") boolean relayLigado,
                    @Value("${pagamento.pix.expiracao-ligada:true}") boolean expiracaoLigada) {
        this.relay = relay;
        this.expirar = expirar;
        this.relayLigado = relayLigado;
        this.expiracaoLigada = expiracaoLigada;
    }

    @Scheduled(fixedDelayString = "${pagamento.outbox.intervalo:1000}")
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

    @Scheduled(fixedDelayString = "${pagamento.pix.intervalo-de-expiracao:60000}")
    void expirarPixVencidos() {
        if (!expiracaoLigada) {
            return;
        }
        try {
            int expirados = expirar.expirarVencidos();
            if (expirados > 0) {
                log.info("pix: {} pagamento(s) expirado(s)", expirados);
            }
        } catch (RuntimeException e) {
            log.warn("pix: falha ao expirar os vencidos: {}", e.getMessage());
        }
    }
}
