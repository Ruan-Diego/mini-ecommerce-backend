package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.memoria.CalculadoraFreteEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.CatalogoConsultaEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Expiração dos pedidos sem pagamento, com relógio controlado pelo teste: sem Spring, sem banco, sem mocks. */
class ExpiracaoDePedidosTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    /** Relógio que o teste adianta à mão. */
    private static class RelogioManual extends Clock {
        private Instant agora = T0;

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final PedidoRepositoryEmMemoria pedidos = new PedidoRepositoryEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final PedidoService service = new PedidoService(pedidos, new CalculadoraFreteEmMemoria(),
            new CatalogoConsultaEmMemoria(), outbox, relogio);

    private Pedido pedidoCriadoEm(Instant criadoEm) {
        Pedido pedido = Pedido.criar(new ClienteId("ana"),
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Esfera", Dinheiro.de("100.00"), 1)),
                new EnderecoEntrega("Ana", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7), criadoEm);
        pedido.limparEventos();
        pedidos.salvar(pedido);
        return pedido;
    }

    private StatusPedido status(Pedido pedido) {
        return pedidos.buscarPorId(pedido.id()).orElseThrow().status();
    }

    @Test
    void antesDos30MinutosNaoCancelaNada() {
        Pedido pedido = pedidoCriadoEm(T0);
        relogio.avancar(Duration.ofMinutes(29).plusSeconds(59));

        assertThat(service.expirarVencidos()).isZero();

        assertThat(status(pedido)).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void comExatamente30MinutosOPedidoSemPagamentoViraCanceladoEPublicaOEvento() {
        Pedido pedido = pedidoCriadoEm(T0);
        relogio.avancar(Duration.ofMinutes(30));

        assertThat(service.expirarVencidos()).isEqualTo(1);

        assertThat(status(pedido)).isEqualTo(StatusPedido.CANCELADO);
        assertThat(outbox.pendentes(10)).extracting(Outbox.Mensagem::tipo).containsExactly("pedido.cancelado");
    }

    @Test
    void cancelaSoOsVencidosEDeixaOsRecentes() {
        Pedido velho = pedidoCriadoEm(T0);
        Pedido novo = pedidoCriadoEm(T0.plus(Duration.ofMinutes(20)));
        relogio.avancar(Duration.ofMinutes(40));

        assertThat(service.expirarVencidos()).isEqualTo(1);

        assertThat(status(velho)).isEqualTo(StatusPedido.CANCELADO);
        assertThat(status(novo)).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void naoMexeNosQueJaForamPagosOuCancelados() {
        Pedido pago = pedidoCriadoEm(T0);
        pago.marcarComoPago(T0.plusSeconds(60));
        pedidos.salvar(pago);
        Pedido cancelado = pedidoCriadoEm(T0);
        cancelado.cancelar(T0.plusSeconds(60));
        pedidos.salvar(cancelado);
        relogio.avancar(Duration.ofHours(2));

        assertThat(service.expirarVencidos()).isZero();

        assertThat(status(pago)).isEqualTo(StatusPedido.PAGO);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void rodarDeNovoNaoCancelaDeNovoNemRepublica() {
        pedidoCriadoEm(T0);
        relogio.avancar(Duration.ofMinutes(45));

        assertThat(service.expirarVencidos()).isEqualTo(1);
        assertThat(service.expirarVencidos()).isZero();

        assertThat(outbox.pendentes(10)).hasSize(1);
    }
}
