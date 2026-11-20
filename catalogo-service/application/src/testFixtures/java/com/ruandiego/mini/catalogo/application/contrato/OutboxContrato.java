package com.ruandiego.mini.catalogo.application.contrato;

import com.ruandiego.mini.catalogo.application.port.out.Outbox;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do par PublicadorEventos (grava) e Outbox (lê e dá baixa). Quem estende devolve os dois, vazios. */
public abstract class OutboxContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PublicadorEventos publicador();

    protected abstract Outbox outbox();

    private static PrecoAlterado precoAlterado(String sku, Instant quando) {
        return new PrecoAlterado(new ProdutoId(UUID.randomUUID()), new Sku(sku), Dinheiro.de("289.90"),
                Dinheiro.de("249.90"), quando);
    }

    @Test
    void eventoPublicadoApareceComoPendenteNaOrdemDeCriacao() {
        publicador().publicar(List.of(precoAlterado("ESF-01", T0), precoAlterado("ESF-02", T0.plusSeconds(1))));

        List<Outbox.Mensagem> pendentes = outbox().pendentes(10);

        assertThat(pendentes).extracting(Outbox.Mensagem::tipo)
                .containsExactly("catalogo.preco-alterado", "catalogo.preco-alterado");
        assertThat(pendentes).allSatisfy(m -> assertThat(m.payload()).isNotBlank());
        assertThat(pendentes.get(0).criadaEm()).isEqualTo(T0);
    }

    @Test
    void respeitaOLimite() {
        publicador().publicar(List.of(precoAlterado("SKU-A", T0), precoAlterado("SKU-B", T0.plusSeconds(1)),
                precoAlterado("SKU-C", T0.plusSeconds(2))));

        assertThat(outbox().pendentes(2)).hasSize(2);
    }

    @Test
    void baixaTiraAMensagemDosPendentesEMantemAsOutras() {
        publicador().publicar(List.of(precoAlterado("SKU-A", T0), precoAlterado("SKU-B", T0.plusSeconds(1))));
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
