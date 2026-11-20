package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.pedido.application.port.out.MensagensProcessadas;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Adapter em memória: um conjunto de (consumidor, mensagem) já vistos. */
public class MensagensProcessadasEmMemoria implements MensagensProcessadas {

    private final Set<String> vistas = new HashSet<>();

    @Override
    public boolean registrar(String consumidor, UUID mensagemId) {
        return vistas.add(consumidor + "|" + mensagemId);
    }

    public void limpar() {
        vistas.clear();
    }
}
