package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.estoque.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstoqueDomainTest {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");
    private static final ProdutoId A = new ProdutoId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final ProdutoId B = new ProdutoId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private static final PedidoId PEDIDO = new PedidoId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    // --- ItemEstoque ---

    @Test
    void estoqueBaixoEhDisponivelAbaixoDeCincoIgnorandoOReservado() {
        assertThat(ItemEstoque.reconstituir(A, 4, 0).estoqueBaixo()).isTrue();
        assertThat(ItemEstoque.reconstituir(A, 0, 0).estoqueBaixo()).isTrue();
        assertThat(ItemEstoque.reconstituir(A, 5, 0).estoqueBaixo()).isFalse();
        assertThat(ItemEstoque.reconstituir(A, 4, 50).estoqueBaixo()).isTrue();
    }

    @Test
    void reservarMoveDoDisponivelParaOReservado() {
        ItemEstoque item = ItemEstoque.novo(A, 10);

        item.reservar(4);

        assertThat(item.disponivel()).isEqualTo(6);
        assertThat(item.reservado()).isEqualTo(4);
    }

    @Test
    void naoReservaMaisDoQueODisponivel() {
        ItemEstoque item = ItemEstoque.novo(A, 3);

        assertThat(item.podeReservar(4)).isFalse();
        assertThatThrownBy(() -> item.reservar(4)).isInstanceOf(RegraDeNegocioException.class);
        assertThat(item.disponivel()).isEqualTo(3);
        assertThat(item.reservado()).isZero();
    }

    @Test
    void reservarTodoODisponivelEPermitido() {
        ItemEstoque item = ItemEstoque.novo(A, 3);

        item.reservar(3);

        assertThat(item.disponivel()).isZero();
        assertThat(item.reservado()).isEqualTo(3);
    }

    @Test
    void liberarDevolveOReservadoAoDisponivel() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 6, 4);

        item.liberar(3);

        assertThat(item.disponivel()).isEqualTo(9);
        assertThat(item.reservado()).isEqualTo(1);
    }

    @Test
    void baixarTiraDoReservadoSemVoltarAoDisponivel() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 6, 4);

        item.baixar(4);

        assertThat(item.disponivel()).isEqualTo(6);
        assertThat(item.reservado()).isZero();
    }

    @Test
    void naoLiberaNemBaixaMaisDoQueOReservado() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 6, 2);

        assertThatThrownBy(() -> item.liberar(3)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.baixar(3)).isInstanceOf(RegraDeNegocioException.class);
        assertThat(item.reservado()).isEqualTo(2);
    }

    @Test
    void recusaQuantidadeZeroENegativaESaldoNegativo() {
        ItemEstoque item = ItemEstoque.novo(A, 5);

        assertThatThrownBy(() -> item.reservar(0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.reservar(-1)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.definirDisponivel(-1)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> ItemEstoque.novo(A, -1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void ajusteDoAdminNaoMexeNoReservado() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 6, 2);

        item.definirDisponivel(20);

        assertThat(item.disponivel()).isEqualTo(20);
        assertThat(item.reservado()).isEqualTo(2);
    }

    // --- Reserva ---

    private static Reserva reservada() {
        return Reserva.reservada(PEDIDO, List.of(new ItemReservado(A, 2), new ItemReservado(B, 1)), T0, T0.plusSeconds(1800));
    }

    @Test
    void reservaNasceReservadaERegistraOEvento() {
        Reserva reserva = reservada();

        assertThat(reserva.status()).isEqualTo(StatusReserva.RESERVADA);
        assertThat(reserva.expiraEm()).isEqualTo(T0.plusSeconds(1800));
        assertThat(reserva.eventosPendentes()).singleElement().isInstanceOfSatisfying(EstoqueReservado.class, e -> {
            assertThat(e.tipo()).isEqualTo("estoque.reservado");
            assertThat(e.pedidoId()).isEqualTo(PEDIDO.toString());
        });
    }

    @Test
    void somaOsItensRepetidosDoMesmoProduto() {
        Reserva reserva = Reserva.reservada(PEDIDO,
                List.of(new ItemReservado(A, 2), new ItemReservado(B, 1), new ItemReservado(A, 3)), T0, T0);

        assertThat(reserva.itens()).containsExactly(new ItemReservado(A, 5), new ItemReservado(B, 1));
    }

    @Test
    void reservaSemItensEhRecusada() {
        assertThatThrownBy(() -> Reserva.reservada(PEDIDO, List.of(), T0, T0))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void confirmarFazABaixaDefinitivaERegistraOEvento() {
        Reserva reserva = reservada();
        reserva.limparEventos();

        reserva.confirmar(T0.plusSeconds(60));

        assertThat(reserva.status()).isEqualTo(StatusReserva.CONFIRMADA);
        assertThat(reserva.eventosPendentes()).singleElement().isInstanceOf(EstoqueBaixado.class);
    }

    @Test
    void liberarDevolveERegistraOEvento() {
        Reserva reserva = reservada();
        reserva.limparEventos();

        reserva.liberar(T0.plusSeconds(60));

        assertThat(reserva.status()).isEqualTo(StatusReserva.LIBERADA);
        assertThat(reserva.eventosPendentes()).singleElement().isInstanceOf(EstoqueLiberado.class);
    }

    @Test
    void soReservadaPodeSerConfirmadaOuLiberada() {
        Reserva confirmada = reservada();
        confirmada.confirmar(T0);
        Reserva liberada = reservada();
        liberada.liberar(T0);
        Reserva indisponivel = Reserva.indisponivel(PEDIDO, List.of(new ItemReservado(A, 1)), List.of(A), T0);

        for (Reserva reserva : List.of(confirmada, liberada, indisponivel)) {
            assertThatThrownBy(() -> reserva.confirmar(T0)).isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> reserva.liberar(T0)).isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Test
    void indisponivelRegistraOEventoComOsProdutosQueFaltaram() {
        Reserva reserva = Reserva.indisponivel(PEDIDO, List.of(new ItemReservado(A, 9), new ItemReservado(B, 1)),
                List.of(A), T0);

        assertThat(reserva.status()).isEqualTo(StatusReserva.INDISPONIVEL);
        assertThat(reserva.eventosPendentes()).singleElement().isInstanceOfSatisfying(EstoqueIndisponivel.class, e -> {
            assertThat(e.tipo()).isEqualTo("estoque.indisponivel");
            assertThat(e.produtosSemEstoque()).containsExactly(A.toString());
        });
    }

    @Test
    void reconstituirNaoRegistraEvento() {
        Reserva reserva = Reserva.reconstituir(PEDIDO, List.of(new ItemReservado(A, 1)), StatusReserva.RESERVADA,
                T0, T0.plusSeconds(1800), T0);

        assertThat(reserva.eventosPendentes()).isEmpty();
    }

    @Test
    void entradaSomaEPerdaSubtraiSemMexerNoReservado() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 6, 2);

        item.entrada(4);
        assertThat(item.disponivel()).isEqualTo(10);
        item.perda(3);
        assertThat(item.disponivel()).isEqualTo(7);
        assertThat(item.reservado()).isEqualTo(2);
    }

    @Test
    void perdaNaoPassaDoDisponivelEQuantidadeTemDeSerPositiva() {
        ItemEstoque item = ItemEstoque.reconstituir(A, 3, 5);

        assertThatThrownBy(() -> item.perda(4)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.perda(0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.entrada(0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item.entrada(-2)).isInstanceOf(RegraDeNegocioException.class);
        assertThat(item.disponivel()).isEqualTo(3);
    }
}
