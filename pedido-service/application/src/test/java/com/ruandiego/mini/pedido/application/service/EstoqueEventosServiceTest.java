package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.memoria.MensagensProcessadasEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDeEstoqueUseCase.EstoqueIndisponivel;
import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.domain.TransicaoDeStatusInvalida;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reação do pedido aos eventos do estoque, sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class EstoqueEventosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");

    private final PedidoRepositoryEmMemoria pedidos = new PedidoRepositoryEmMemoria();
    private final MensagensProcessadasEmMemoria processadas = new MensagensProcessadasEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final EstoqueEventosService service = new EstoqueEventosService(pedidos, processadas, outbox,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private Pedido pedidoNoStatus(StatusPedido status) {
        Pedido pedido = Pedido.reconstituir(PedidoId.novo(), new ClienteId("ana"),
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Esfera", Dinheiro.de("100.00"), 2)),
                new EnderecoEntrega("Ana", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7), status, AGORA, AGORA);
        pedidos.salvar(pedido);
        return pedido;
    }

    private EstoqueIndisponivel indisponivel(Pedido pedido) {
        return new EstoqueIndisponivel(UUID.randomUUID(), pedido.id());
    }

    private StatusPedido status(Pedido pedido) {
        return pedidos.buscarPorId(pedido.id()).orElseThrow().status();
    }

    @Test
    void estoqueIndisponivelCancelaOPedidoQueAguardaPagamentoEGravaOEvento() {
        Pedido pedido = pedidoNoStatus(StatusPedido.AGUARDANDO_PAGAMENTO);

        service.indisponivel(indisponivel(pedido));

        assertThat(status(pedido)).isEqualTo(StatusPedido.CANCELADO);
        assertThat(outbox.pendentes(10)).extracting(Outbox.Mensagem::tipo).containsExactly("pedido.cancelado");
    }

    @Test
    void cancelaTambemOPedidoQueJaFoiPagoParaOPagamentoEstornar() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PAGO);

        service.indisponivel(indisponivel(pedido));

        assertThat(status(pedido)).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void mensagemRepetidaNaoCancelaDeNovoNemRepublica() {
        Pedido pedido = pedidoNoStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        EstoqueIndisponivel evento = indisponivel(pedido);

        service.indisponivel(evento);
        service.indisponivel(evento);

        assertThat(outbox.pendentes(10)).hasSize(1);
    }

    @Test
    void pedidoJaCanceladoPorOutraViaNaoMudaNada() {
        Pedido pedido = pedidoNoStatus(StatusPedido.CANCELADO);

        service.indisponivel(indisponivel(pedido));

        assertThat(status(pedido)).isEqualTo(StatusPedido.CANCELADO);
        assertThat(outbox.pendentes(10)).isEmpty();
    }

    @Test
    void pedidoJaEnviadoNaoCancelaELancaParaIrParaAFilaMorta() {
        Pedido pedido = pedidoNoStatus(StatusPedido.ENVIADO);

        assertThatThrownBy(() -> service.indisponivel(indisponivel(pedido)))
                .isInstanceOf(TransicaoDeStatusInvalida.class);
        assertThat(status(pedido)).isEqualTo(StatusPedido.ENVIADO);
    }

    @Test
    void pedidoInexistenteLanca() {
        assertThatThrownBy(() -> service.indisponivel(new EstoqueIndisponivel(UUID.randomUUID(), PedidoId.novo())))
                .isInstanceOf(NaoEncontradoException.class);
    }
}
