package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.kernel.DomainEvent;
import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Outbox em memória: grava os eventos (como o adapter JPA faz na tabela) e deixa o relay ler e dar baixa.
 * O payload é só o texto do evento; o JSON de verdade é coisa do adapter real.
 */
public class OutboxEmMemoria implements PublicadorEventos, Outbox {

    private record Linha(Mensagem mensagem, Instant publicadaEm) {
    }

    private final List<Linha> linhas = new ArrayList<>();

    @Override
    public void publicar(List<? extends DomainEvent> eventos) {
        for (DomainEvent evento : eventos) {
            linhas.add(new Linha(new Mensagem(UUID.randomUUID(), evento.tipo(), evento.toString(), evento.ocorridoEm()), null));
        }
    }

    @Override
    public List<Mensagem> pendentes(int limite) {
        return linhas.stream().filter(l -> l.publicadaEm() == null).map(Linha::mensagem).limit(limite).toList();
    }

    @Override
    public void marcarComoPublicada(UUID id, Instant publicadaEm) {
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).mensagem().id().equals(id)) {
                linhas.set(i, new Linha(linhas.get(i).mensagem(), publicadaEm));
            }
        }
    }

    /** Tipos de todas as mensagens gravadas, publicadas ou não, em ordem de criação. */
    public List<String> tiposGravados() {
        return linhas.stream().map(l -> l.mensagem().tipo()).toList();
    }

    public void limpar() {
        linhas.clear();
    }
}
