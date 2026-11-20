package com.ruandiego.mini.estoque.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leitura e baixa das mensagens da outbox, usadas pelo relay. */
public interface Outbox {

    record Mensagem(UUID id, String tipo, String payload, Instant criadaEm, String traceId) {
    }

    /** Mensagens ainda não publicadas, da mais antiga para a mais nova (a ordem de criação é preservada). */
    List<Mensagem> pendentes(int limite);

    void marcarComoPublicada(UUID id, Instant publicadaEm);
}
