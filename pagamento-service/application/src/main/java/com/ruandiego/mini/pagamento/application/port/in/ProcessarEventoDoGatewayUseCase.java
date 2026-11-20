package com.ruandiego.mini.pagamento.application.port.in;

/** Processa o webhook do gateway. Idempotente: o mesmo evento da mesma transação só tem efeito uma vez. */
public interface ProcessarEventoDoGatewayUseCase {

    enum Evento {
        APROVADO,
        RECUSADO,
        ESTORNADO
    }

    enum Resultado {
        PROCESSADO,
        /** O evento já tinha sido tratado (reenvio do gateway): nada mudou e nenhum evento novo saiu. */
        JA_PROCESSADO
    }

    Resultado processar(String transacaoId, Evento evento);
}
