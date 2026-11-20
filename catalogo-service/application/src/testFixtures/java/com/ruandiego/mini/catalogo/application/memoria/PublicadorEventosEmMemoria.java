package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.kernel.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/** Adapter em memória do PublicadorEventos: guarda o que foi publicado para o teste conferir. */
public class PublicadorEventosEmMemoria implements PublicadorEventos {

    private final List<DomainEvent> publicados = new ArrayList<>();

    @Override
    public void publicar(List<? extends DomainEvent> eventos) {
        publicados.addAll(eventos);
    }

    public void limpar() {
        publicados.clear();
    }

    public List<DomainEvent> publicados() {
        return List.copyOf(publicados);
    }
}
