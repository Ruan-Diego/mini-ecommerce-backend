package com.ruandiego.mini.estoque.application.service;

import com.ruandiego.mini.estoque.application.NaoEncontradoException;
import com.ruandiego.mini.estoque.application.memoria.ItemEstoqueRepositoryEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.MensagensProcessadasEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.ReservaRepositoryEmMemoria;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCancelado;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCriado;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoPago;
import com.ruandiego.mini.estoque.application.port.out.Outbox;
import com.ruandiego.mini.estoque.domain.RegraDeNegocioException;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import com.ruandiego.mini.estoque.domain.model.StatusReserva;
import com.ruandiego.mini.estoque.domain.model.TipoDeAjuste;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes do service sem Spring e sem mocks: só os adapters em memória. */
class EstoqueServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T12:00:00Z");
    private static final ProdutoId A = new ProdutoId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final ProdutoId B = new ProdutoId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private final ItemEstoqueRepositoryEmMemoria itens = new ItemEstoqueRepositoryEmMemoria();
    private final ReservaRepositoryEmMemoria reservas = new ReservaRepositoryEmMemoria();
    private final MensagensProcessadasEmMemoria processadas = new MensagensProcessadasEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final EstoqueService service = new EstoqueService(itens, reservas, processadas, outbox,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private static PedidoId novoPedido() {
        return new PedidoId(UUID.randomUUID());
    }

    private PedidoCriado criado(PedidoId pedido, ItemReservado... pedidos) {
        return new PedidoCriado(UUID.randomUUID(), pedido, List.of(pedidos));
    }

    private List<String> tiposPublicados() {
        return outbox.pendentes(100).stream().map(Outbox.Mensagem::tipo).toList();
    }

    @Test
    void reservaMoveOSaldoEPublicaEstoqueReservado() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();

        service.criado(criado(pedido, new ItemReservado(A, 4)));

        assertThat(itens.buscar(A)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(6);
            assertThat(i.reservado()).isEqualTo(4);
        });
        assertThat(reservas.buscarPorPedido(pedido)).hasValueSatisfying(r -> {
            assertThat(r.status()).isEqualTo(StatusReserva.RESERVADA);
            assertThat(r.expiraEm()).isEqualTo(AGORA.plus(EstoqueService.VALIDADE_DA_RESERVA));
        });
        assertThat(tiposPublicados()).containsExactly("estoque.reservado");
    }

    @Test
    void semSaldoSuficienteNaoSeparaNadaEPublicaEstoqueIndisponivel() {
        itens.salvar(ItemEstoque.novo(A, 10));
        itens.salvar(ItemEstoque.novo(B, 1));
        PedidoId pedido = novoPedido();

        service.criado(criado(pedido, new ItemReservado(A, 2), new ItemReservado(B, 5)));

        // Tudo ou nada: o item A tinha saldo, mas como o B faltou, nada foi separado.
        assertThat(itens.buscar(A)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(10);
            assertThat(i.reservado()).isZero();
        });
        assertThat(reservas.buscarPorPedido(pedido)).hasValueSatisfying(
                r -> assertThat(r.status()).isEqualTo(StatusReserva.INDISPONIVEL));
        assertThat(tiposPublicados()).containsExactly("estoque.indisponivel");
    }

    @Test
    void produtoSemEstoqueCadastradoEhIndisponivel() {
        PedidoId pedido = novoPedido();

        service.criado(criado(pedido, new ItemReservado(A, 1)));

        assertThat(tiposPublicados()).containsExactly("estoque.indisponivel");
    }

    @Test
    void somaOsItensRepetidosAntesDeConferirOSaldo() {
        itens.salvar(ItemEstoque.novo(A, 5));

        service.criado(criado(novoPedido(), new ItemReservado(A, 3), new ItemReservado(A, 3)));

        assertThat(tiposPublicados()).containsExactly("estoque.indisponivel");
        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(5);
    }

    @Test
    void mensagemRepetidaNaoReservaDeNovo() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoCriado evento = criado(novoPedido(), new ItemReservado(A, 4));

        service.criado(evento);
        service.criado(evento);

        assertThat(itens.buscar(A).orElseThrow().reservado()).isEqualTo(4);
        assertThat(tiposPublicados()).hasSize(1);
    }

    @Test
    void mesmoPedidoComOutraMensagemTambemNaoReservaDeNovo() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();

        service.criado(criado(pedido, new ItemReservado(A, 4)));
        service.criado(criado(pedido, new ItemReservado(A, 4)));

        assertThat(itens.buscar(A).orElseThrow().reservado()).isEqualTo(4);
    }

    @Test
    void pedidoPagoFazABaixaDefinitiva() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4)));

        service.pago(new PedidoPago(UUID.randomUUID(), pedido));

        assertThat(itens.buscar(A)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(6);
            assertThat(i.reservado()).isZero();
        });
        assertThat(reservas.buscarPorPedido(pedido).orElseThrow().status()).isEqualTo(StatusReserva.CONFIRMADA);
        assertThat(tiposPublicados()).containsExactly("estoque.reservado", "estoque.baixado");
    }

    @Test
    void pedidoPagoRepetidoNaoBaixaDuasVezes() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4)));
        PedidoPago pago = new PedidoPago(UUID.randomUUID(), pedido);

        service.pago(pago);
        service.pago(pago);
        service.pago(new PedidoPago(UUID.randomUUID(), pedido)); // outra mensagem, mesmo pedido

        assertThat(itens.buscar(A).orElseThrow().reservado()).isZero();
        assertThat(tiposPublicados()).containsExactly("estoque.reservado", "estoque.baixado");
    }

    @Test
    void pedidoCanceladoDevolveOEstoque() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4)));

        service.cancelado(new PedidoCancelado(UUID.randomUUID(), pedido));

        assertThat(itens.buscar(A)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(10);
            assertThat(i.reservado()).isZero();
        });
        assertThat(reservas.buscarPorPedido(pedido).orElseThrow().status()).isEqualTo(StatusReserva.LIBERADA);
        assertThat(tiposPublicados()).containsExactly("estoque.reservado", "estoque.liberado");
    }

    @Test
    void cancelarDuasVezesNaoDevolveDuasVezes() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4)));

        service.cancelado(new PedidoCancelado(UUID.randomUUID(), pedido));
        service.cancelado(new PedidoCancelado(UUID.randomUUID(), pedido));

        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(10);
    }

    @Test
    void cancelarPedidoSemEstoqueNaoMudaNada() {
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4))); // sem saldo: INDISPONIVEL

        service.cancelado(new PedidoCancelado(UUID.randomUUID(), pedido));

        assertThat(reservas.buscarPorPedido(pedido).orElseThrow().status()).isEqualTo(StatusReserva.INDISPONIVEL);
        assertThat(tiposPublicados()).containsExactly("estoque.indisponivel");
    }

    @Test
    void cancelarPedidoJaPagoNaoDevolveAEstoqueBaixado() {
        itens.salvar(ItemEstoque.novo(A, 10));
        PedidoId pedido = novoPedido();
        service.criado(criado(pedido, new ItemReservado(A, 4)));
        service.pago(new PedidoPago(UUID.randomUUID(), pedido));

        service.cancelado(new PedidoCancelado(UUID.randomUUID(), pedido));

        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(6);
        assertThat(reservas.buscarPorPedido(pedido).orElseThrow().status()).isEqualTo(StatusReserva.CONFIRMADA);
    }

    @Test
    void pagoOuCanceladoSemReservaLancaParaIrParaAFilaMorta() {
        assertThatThrownBy(() -> service.pago(new PedidoPago(UUID.randomUUID(), novoPedido())))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.cancelado(new PedidoCancelado(UUID.randomUUID(), novoPedido())))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void pedidoSemItensLanca() {
        assertThatThrownBy(() -> service.criado(criado(novoPedido())))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void consultaOSaldoOuFalhaSeNaoExiste() {
        itens.salvar(ItemEstoque.reconstituir(A, 7, 2));

        ItemEstoque item = service.consultar(A);

        assertThat(item.disponivel()).isEqualTo(7);
        assertThat(item.reservado()).isEqualTo(2);
        assertThatThrownBy(() -> service.consultar(B)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void ajusteDoAdminCriaOuAtualizaOSaldoSemMexerNoReservado() {
        service.definirDisponivel(A, 12);
        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(12);

        service.criado(criado(novoPedido(), new ItemReservado(A, 5)));
        service.definirDisponivel(A, 30);

        assertThat(itens.buscar(A)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(30);
            assertThat(i.reservado()).isEqualTo(5);
        });
        assertThatThrownBy(() -> service.definirDisponivel(A, -1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void ajusteComMotivoEntradaPerdaECorrecaoRegistraOAntesEODepois() {
        itens.salvar(ItemEstoque.novo(A, 10));

        service.ajustar(A, TipoDeAjuste.ENTRADA, 5);
        service.ajustar(A, TipoDeAjuste.PERDA, 3);
        service.ajustar(A, TipoDeAjuste.CORRECAO, 20);

        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(20);
        assertThat(tiposPublicados()).containsExactly("estoque.ajustado", "estoque.ajustado", "estoque.ajustado");
        assertThat(outbox.pendentes(100)).extracting(Outbox.Mensagem::payload).satisfiesExactly(
                p -> assertThat(p).contains("ajuste=ENTRADA", "disponivelAntes=10", "disponivelDepois=15"),
                p -> assertThat(p).contains("ajuste=PERDA", "disponivelAntes=15", "disponivelDepois=12"),
                p -> assertThat(p).contains("ajuste=CORRECAO", "disponivelAntes=12", "disponivelDepois=20"));
    }

    @Test
    void entradaDeProdutoSemEstoqueCadastradoCriaOItem() {
        service.ajustar(A, TipoDeAjuste.ENTRADA, 8);

        assertThat(itens.buscar(A)).hasValueSatisfying(i -> assertThat(i.disponivel()).isEqualTo(8));
    }

    @Test
    void perdaMaiorQueODisponivelEhRecusadaSemGravarNemPublicar() {
        itens.salvar(ItemEstoque.novo(A, 2));

        assertThatThrownBy(() -> service.ajustar(A, TipoDeAjuste.PERDA, 3)).isInstanceOf(RegraDeNegocioException.class);

        assertThat(itens.buscar(A).orElseThrow().disponivel()).isEqualTo(2);
        assertThat(tiposPublicados()).isEmpty();
    }
}
