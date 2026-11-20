package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.pedido.application.port.out.BrokerMensagens;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Broker de teste: guarda o que recebeu e pode ser ligado para falhar (broker fora do ar). */
public class BrokerMensagensEmMemoria implements BrokerMensagens {

    public record Publicada(UUID id, String tipo, String payload, String traceId) {
    }

    private final List<Publicada> recebidas = new ArrayList<>();
    private boolean foraDoAr;

    public void ficarForaDoAr(boolean foraDoAr) {
        this.foraDoAr = foraDoAr;
    }

    @Override
    public void publicar(UUID id, String tipo, String payload, String traceId) {
        if (foraDoAr) {
            throw new IllegalStateException("broker fora do ar");
        }
        recebidas.add(new Publicada(id, tipo, payload, traceId));
    }

    public List<Publicada> recebidas() {
        return recebidas;
    }
}
