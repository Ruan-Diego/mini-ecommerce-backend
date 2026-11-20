package com.ruandiego.mini.pagamento.application.memoria;

import com.ruandiego.mini.pagamento.application.port.out.BrokerMensagens;

import java.util.ArrayList;
import java.util.List;

/** Broker de teste: guarda o que recebeu e pode ser ligado para falhar (broker fora do ar). */
public class BrokerMensagensEmMemoria implements BrokerMensagens {

    public record Publicada(String tipo, String payload) {
    }

    private final List<Publicada> recebidas = new ArrayList<>();
    private boolean foraDoAr;

    public void ficarForaDoAr(boolean foraDoAr) {
        this.foraDoAr = foraDoAr;
    }

    @Override
    public void publicar(String tipo, String payload) {
        if (foraDoAr) {
            throw new IllegalStateException("broker fora do ar");
        }
        recebidas.add(new Publicada(tipo, payload));
    }

    public List<Publicada> recebidas() {
        return recebidas;
    }
}
