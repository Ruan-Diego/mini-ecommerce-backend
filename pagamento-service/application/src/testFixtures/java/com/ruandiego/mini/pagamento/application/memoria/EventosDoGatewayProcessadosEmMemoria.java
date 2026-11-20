package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;

import java.util.HashSet;
import java.util.Set;

/** Adapter em memória: um conjunto de (transação, evento) já vistos. */
public class EventosDoGatewayProcessadosEmMemoria implements EventosDoGatewayProcessados {

    private final Set<String> vistos = new HashSet<>();

    @Override
    public boolean registrar(String transacaoId, String evento) {
        return vistos.add(transacaoId + "|" + evento);
    }

    public void limpar() {
        vistos.clear();
    }
}
