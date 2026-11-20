package com.ruandiego.mini.pedido.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.memoria.MensagensProcessadasEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase.PagamentoAprovado;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase.PagamentoEstornado;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
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

/** Reação do pedido aos eventos do pagamento, sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class PagamentoEventosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");
    private static final ClienteId ANA = new ClienteId("ana");

    private final PedidoRepositoryEmMemoria pedidos = new PedidoRepositoryEmMemoria();
    private final MensagensProcessadasEmMemoria processadas = new MensagensProcessadasEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final PagamentoEventosService service = new PagamentoEventosService(pedidos, processadas, outbox,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    /** Pedido de 2 x R$ 100,00 + frete de R$ 19,90 = R$ 219,90, aguardando pagamento. */
    private Pedido pedidoAguardando() {
        Pedido pedido = Pedido.criar(ANA,
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Esfera", Dinheiro.de("100.00"), 2)),
                new EnderecoEntrega("Ana", "01310100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7), AGORA);
        pedido.limparEventos();
        pedidos.salvar(pedido);
        return pedido;
    }

    private PagamentoAprovado aprovado(Pedido pedido, ClienteId cliente, String valor) {
        return new PagamentoAprovado(UUID.randomUUID(), pedido.id(), cliente, Dinheiro.de(valor));
    }

    private StatusPedido status(Pedido pedido) {
        return pedidos.buscarPorId(pedido.id()).orElseThrow().status();
    }

    // ---- pagamento aprovado ----

    @Test
    void pagamentoAprovadoMarcaOPedidoComoPagoEGravaOEvento() {
        Pedido pedido = pedidoAguardando();

        service.aprovado(aprovado(pedido, ANA, "219.90"));

        assertThat(status(pedido)).isEqualTo(StatusPedido.PAGO);
        assertThat(outbox.tiposGravados()).containsExactly("pedido.pago");
    }

    @Test
    void amesmaMensagemEntregueDeNovoNaoFazNada() {
        Pedido pedido = pedidoAguardando();
        PagamentoAprovado mensagem = aprovado(pedido, ANA, "219.90");

        service.aprovado(mensagem);
        service.aprovado(mensagem);
        service.aprovado(mensagem);

        assertThat(status(pedido)).isEqualTo(StatusPedido.PAGO);
        assertThat(outbox.tiposGravados()).containsExactly("pedido.pago");
    }

    @Test
    void outraMensagemParaUmPedidoJaPagoNaoMudaNada() {
        Pedido pedido = pedidoAguardando();
        service.aprovado(aprovado(pedido, ANA, "219.90"));

        service.aprovado(aprovado(pedido, ANA, "219.90"));

        assertThat(outbox.tiposGravados()).containsExactly("pedido.pago");
    }

    @Test
    void recusaValorQueNaoConfereComOTotalDoPedido() {
        Pedido pedido = pedidoAguardando();

        assertThatThrownBy(() -> service.aprovado(aprovado(pedido, ANA, "1.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não é o total");

        assertThat(status(pedido)).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void recusaPagamentoDeOutroCliente() {
        Pedido pedido = pedidoAguardando();

        assertThatThrownBy(() -> service.aprovado(aprovado(pedido, new ClienteId("beto"), "219.90")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("outro cliente");

        assertThat(status(pedido)).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void pedidoInexistenteEhNaoEncontrado() {
        assertThatThrownBy(() -> service.aprovado(
                new PagamentoAprovado(UUID.randomUUID(), PedidoId.novo(), ANA, Dinheiro.de("10.00"))))
                .isInstanceOf(NaoEncontradoException.class);
    }

    // ---- pagamento estornado ----

    @Test
    void estornoCancelaOPedidoPagoEGravaOEvento() {
        Pedido pedido = pedidoAguardando();
        service.aprovado(aprovado(pedido, ANA, "219.90"));

        service.estornado(new PagamentoEstornado(UUID.randomUUID(), pedido.id()));

        assertThat(status(pedido)).isEqualTo(StatusPedido.CANCELADO);
        assertThat(outbox.tiposGravados()).containsExactly("pedido.pago", "pedido.cancelado");
    }

    @Test
    void estornoDoPedidoJaCanceladoNaoFazNadaEMensagemRepetidaTambemNao() {
        Pedido pedido = pedidoAguardando();
        Pedido cancelado = pedidos.buscarPorId(pedido.id()).orElseThrow();
        cancelado.cancelar(AGORA);
        pedidos.salvar(cancelado);
        PagamentoEstornado mensagem = new PagamentoEstornado(UUID.randomUUID(), pedido.id());

        service.estornado(mensagem);
        service.estornado(mensagem);

        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void estornoDePedidoJaEnviadoEhRecusadoPeloDominio() {
        Pedido pedido = pedidoAguardando();
        Pedido enviado = pedidos.buscarPorId(pedido.id()).orElseThrow();
        enviado.marcarComoPago(AGORA);
        enviado.iniciarSeparacao(AGORA);
        enviado.enviar(AGORA);
        pedidos.salvar(enviado);

        assertThatThrownBy(() -> service.estornado(new PagamentoEstornado(UUID.randomUUID(), pedido.id())))
                .isInstanceOf(TransicaoDeStatusInvalida.class);

        assertThat(status(pedido)).isEqualTo(StatusPedido.ENVIADO);
    }
}
