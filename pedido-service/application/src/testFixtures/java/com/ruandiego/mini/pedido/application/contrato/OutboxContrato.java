package com.ruandiego.mini.pedido.application.contrato;

import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.domain.model.PedidoCriado;
import com.ruandiego.mini.pedido.domain.model.PedidoStatusAlterado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do par PublicadorEventos (grava) e Outbox (lê e dá baixa). Quem estende devolve os dois, vazios. */
public abstract class OutboxContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PublicadorEventos publicador();

    protected abstract Outbox outbox();

    @Test
    void eventoPublicadoApareceComoPendenteNaOrdemDeCriacao() {
        publicador().publicar(List.of(
                new PedidoCriado("p1", "c1", new BigDecimal("270.40"), T0),
                new PedidoStatusAlterado("p1", "c1", "AGUARDANDO_PAGAMENTO", "PAGO", T0.plusSeconds(5))));

        List<Outbox.Mensagem> pendentes = outbox().pendentes(10);

        assertThat(pendentes).extracting(Outbox.Mensagem::tipo).containsExactly("pedido.criado", "pedido.pago");
        assertThat(pendentes).allSatisfy(m -> assertThat(m.payload()).isNotBlank());
        assertThat(pendentes.get(0).criadaEm()).isEqualTo(T0);
    }

    @Test
    void respeitaOLimite() {
        publicador().publicar(List.of(
                new PedidoCriado("p1", "c1", BigDecimal.TEN, T0),
                new PedidoCriado("p2", "c1", BigDecimal.TEN, T0.plusSeconds(1)),
                new PedidoCriado("p3", "c1", BigDecimal.TEN, T0.plusSeconds(2))));

        assertThat(outbox().pendentes(2)).hasSize(2);
    }

    @Test
    void baixaTiraAMensagemDosPendentesEMantemAsOutras() {
        publicador().publicar(List.of(
                new PedidoCriado("p1", "c1", BigDecimal.TEN, T0),
                new PedidoCriado("p2", "c1", BigDecimal.TEN, T0.plusSeconds(1))));
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
