package com.ruandiego.mini.estoque.application.contrato;

import com.ruandiego.mini.estoque.application.port.out.Outbox;
import com.ruandiego.mini.estoque.application.port.out.PublicadorEventos;
import com.ruandiego.mini.estoque.domain.model.EstoqueIndisponivel;
import com.ruandiego.mini.estoque.domain.model.EstoqueLiberado;
import com.ruandiego.mini.estoque.domain.model.EstoqueReservado;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do par PublicadorEventos (grava) e Outbox (lê e dá baixa). Quem estende devolve os dois, vazios. */
public abstract class OutboxContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PublicadorEventos publicador();

    protected abstract Outbox outbox();

    private static EstoqueReservado aprovado(String pedido, Instant quando) {
        return new EstoqueReservado(pedido, quando);
    }

    @Test
    void eventoPublicadoApareceComoPendenteNaOrdemDeCriacao() {
        publicador().publicar(List.of(
                aprovado("pg-1", T0),
                new EstoqueIndisponivel("pg-2", List.of("prod-1"), T0.plusSeconds(1)),
                new EstoqueLiberado("pg-1", T0.plusSeconds(2))));

        List<Outbox.Mensagem> pendentes = outbox().pendentes(10);

        assertThat(pendentes).extracting(Outbox.Mensagem::tipo)
                .containsExactly("estoque.reservado", "estoque.indisponivel", "estoque.liberado");
        assertThat(pendentes).allSatisfy(m -> assertThat(m.payload()).isNotBlank());
        assertThat(pendentes.get(0).criadaEm()).isEqualTo(T0);
    }

    @Test
    void respeitaOLimite() {
        publicador().publicar(List.of(aprovado("pg-1", T0), aprovado("pg-2", T0.plusSeconds(1)),
                aprovado("pg-3", T0.plusSeconds(2))));

        assertThat(outbox().pendentes(2)).hasSize(2);
    }

    @Test
    void baixaTiraAMensagemDosPendentesEMantemAsOutras() {
        publicador().publicar(List.of(aprovado("pg-1", T0), aprovado("pg-2", T0.plusSeconds(1))));
        Outbox.Mensagem primeira = outbox().pendentes(10).get(0);

        outbox().marcarComoPublicada(primeira.id(), T0.plusSeconds(30));

        assertThat(outbox().pendentes(10)).hasSize(1).doesNotContain(primeira);
    }

    @Test
    void semEventosNaoHaPendentes() {
        publicador().publicar(List.of());

        assertThat(outbox().pendentes(10)).isEmpty();
    }
}
