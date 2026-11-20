package com.ruandiego.mini.pedido.application.port.out;

import java.util.UUID;

/**
 * Registro das mensagens que um consumidor já tratou, uma linha por (consumidor, mensagem). É o que
 * torna o consumo idempotente mesmo com duas entregas simultâneas da mesma mensagem: só a primeira registra.
 */
public interface MensagensProcessadas {

    /** @return true se esta mensagem é nova para o consumidor e foi registrada; false se já tinha sido tratada. */
    boolean registrar(String consumidor, UUID mensagemId);
}
