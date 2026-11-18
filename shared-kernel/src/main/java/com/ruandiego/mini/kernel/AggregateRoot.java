package com.ruandiego.mini.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Raiz de agregado: registra eventos de domínio para serem publicados após a persistência. */
public abstract class AggregateRoot {

    private final List<DomainEvent> eventos = new ArrayList<>();

    protected void registrar(DomainEvent evento) {
        eventos.add(evento);
    }

    public List<DomainEvent> eventosPendentes() {
        return Collections.unmodifiableList(eventos);
    }

    public void limparEventos() {
        eventos.clear();
    }
}
