package com.ruandiego.mini.pagamento.application.service;

import com.ruandiego.mini.pagamento.application.memoria.BrokerMensagensEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pagamento.domain.model.PagamentoAprovado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelayDoOutboxTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");

    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final BrokerMensagensEmMemoria broker = new BrokerMensagensEmMemoria();
    private final RelayDoOutbox relay = new RelayDoOutbox(outbox, broker, Clock.fixed(AGORA, ZoneOffset.UTC));

    private void gravar(String... pedidos) {
        for (String pedido : pedidos) {
            outbox.publicar(List.of(new PagamentoAprovado(pedido, "ped", "c1", BigDecimal.TEN, "tx", AGORA)));
        }
    }

    @Test
    void semNadaGravadoNadaVaiParaOBroker() {
        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(broker.recebidas()).isEmpty();
    }

    @Test
    void publicaNaOrdemDeCriacaoEDaBaixa() {
        gravar("p1", "p2");

        assertThat(relay.publicarPendentes(10)).isEqualTo(2);

        assertThat(broker.recebidas()).extracting(m -> m.payload().contains("p1")).containsExactly(true, false);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void naoRepublicaOQueJaFoiPublicado() {
        gravar("p1");
        relay.publicarPendentes(10);

        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(broker.recebidas()).hasSize(1);
    }

    @Test
    void comOBrokerForaDoArMantemTudoPendenteEPublicaQuandoVoltar() {
        gravar("p1", "p2");
        broker.ficarForaDoAr(true);

        assertThat(relay.publicarPendentes(10)).isZero();
        assertThat(outbox.pendentes(10)).hasSize(2);

        broker.ficarForaDoAr(false);
        assertThat(relay.publicarPendentes(10)).isEqualTo(2);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void respeitaOLimiteDaRodada() {
        gravar("p1", "p2", "p3");

        assertThat(relay.publicarPendentes(2)).isEqualTo(2);
        assertThat(outbox.pendentes(10)).hasSize(1);
    }
}
