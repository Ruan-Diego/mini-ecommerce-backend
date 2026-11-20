package com.ruandiego.mini.pagamento.application.port.out;

/**
 * Registro dos eventos do gateway já tratados, uma linha por (transação, evento). É o que torna o webhook
 * idempotente mesmo com duas entregas simultâneas: só a primeira consegue registrar.
 */
public interface EventosDoGatewayProcessados {

    /** @return true se este (transação, evento) é novo e foi registrado; false se já tinha sido tratado. */
    boolean registrar(String transacaoId, String evento);
}
