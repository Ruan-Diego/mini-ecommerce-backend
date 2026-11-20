package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.pedido.application.port.out.BrokerMensagens;
import com.ruandiego.mini.pedido.application.port.out.Outbox;

import java.time.Clock;

/**
 * Relay da outbox: lê as mensagens pendentes (já confirmadas no banco) e as publica no broker, na ordem
 * em que foram criadas. Só mensagens commitadas são visíveis na outbox, e é daí que vem a garantia do
 * padrão: nenhum evento chega ao broker antes do commit do pedido.
 *
 * A entrega é "pelo menos uma vez": se o broker confirmar e a baixa falhar (queda no meio), a mensagem
 * sai de novo na próxima rodada. Quem consome precisa ser idempotente (a do carrinho já é).
 * Se o broker falhar, o relay para na primeira falha e mantém a ordem: as demais ficam para a próxima rodada.
 * Uma só instância deve rodar o relay; com várias, a mesma mensagem pode sair duplicada.
 */
public class RelayDoOutbox {

    private final Outbox outbox;
    private final BrokerMensagens broker;
    private final Clock relogio;

    public RelayDoOutbox(Outbox outbox, BrokerMensagens broker, Clock relogio) {
        this.outbox = outbox;
        this.broker = broker;
        this.relogio = relogio;
    }

    /** Publica até `limite` mensagens pendentes e devolve quantas saíram. */
    public int publicarPendentes(int limite) {
        int publicadas = 0;
        for (Outbox.Mensagem mensagem : outbox.pendentes(limite)) {
            try {
                broker.publicar(mensagem.id(), mensagem.tipo(), mensagem.payload(), mensagem.traceId());
            } catch (RuntimeException e) {
                return publicadas;
            }
            outbox.marcarComoPublicada(mensagem.id(), relogio.instant());
            publicadas++;
        }
        return publicadas;
    }
}
