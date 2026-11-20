package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.memoria.BrokerMensagensEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;


import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RelayDoOutboxTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");

    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final BrokerMensagensEmMemoria broker = new BrokerMensagensEmMemoria();
    private final RelayDoOutbox relay = new RelayDoOutbox(outbox, broker, Clock.fixed(AGORA, ZoneOffset.UTC));

    private void gravar(String... skus) {
        for (String sku : skus) {
            outbox.publicar(List.of(new PrecoAlterado(new ProdutoId(UUID.randomUUID()), new Sku(sku), Dinheiro.de("10.00"), Dinheiro.de("9.00"), AGORA)));
        }
    }

    @Test
    void semNadaGravadoNadaVaiParaOBroker() {
        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(broker.recebidas()).isEmpty();
    }

    @Test
    void publicaNaOrdemDeCriacaoEDaBaixa() {
        gravar("sku-1", "sku-2");

        assertThat(relay.publicarPendentes(10)).isEqualTo(2);

        assertThat(broker.recebidas()).extracting(m -> m.payload().contains("SKU-1")).containsExactly(true, false);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void naoRepublicaOQueJaFoiPublicado() {
        gravar("sku-1");
        relay.publicarPendentes(10);

        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(broker.recebidas()).hasSize(1);
    }

    @Test
    void comOBrokerForaDoArMantemTudoPendenteEPublicaQuandoVoltar() {
        gravar("sku-1", "sku-2");
        broker.ficarForaDoAr(true);

        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(outbox.pendentes(10)).hasSize(2);

        broker.ficarForaDoAr(false);
        assertThat(relay.publicarPendentes(10)).isEqualTo(2);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void respeitaOLimiteDaRodada() {
        gravar("sku-1", "sku-2", "sku-3");

        assertThat(relay.publicarPendentes(2)).isEqualTo(2);
        assertThat(outbox.pendentes(10)).hasSize(1);
    }
}
