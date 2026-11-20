package com.ruandiego.mini.estoque.application.contrato;

import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import com.ruandiego.mini.estoque.domain.model.Reserva;
import com.ruandiego.mini.estoque.domain.model.StatusReserva;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do port ReservaRepository. Quem estende devolve um repositório vazio a cada teste. */
public abstract class ReservaRepositoryContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    protected abstract ReservaRepository reservas();

    private static PedidoId pedido() {
        return new PedidoId(UUID.randomUUID());
    }

    private static List<ItemReservado> itens() {
        return List.of(new ItemReservado(new ProdutoId(UUID.randomUUID()), 2),
                new ItemReservado(new ProdutoId(UUID.randomUUID()), 1));
    }

    @Test
    void salvaEBuscaPorPedidoComOsItens() {
        PedidoId pedido = pedido();
        List<ItemReservado> itens = itens();

        reservas().salvar(Reserva.reservada(pedido, itens, T0, T0.plusSeconds(1800)));

        assertThat(reservas().buscarPorPedido(pedido)).hasValueSatisfying(r -> {
            assertThat(r.status()).isEqualTo(StatusReserva.RESERVADA);
            assertThat(r.itens()).containsExactlyInAnyOrderElementsOf(itens);
            assertThat(r.expiraEm()).isEqualTo(T0.plusSeconds(1800));
            assertThat(r.criadaEm()).isEqualTo(T0);
            assertThat(r.eventosPendentes()).isEmpty();
        });
    }

    @Test
    void pedidoSemReservaNaoApareceNaBusca() {
        assertThat(reservas().buscarPorPedido(pedido())).isEmpty();
        assertThat(reservas().buscarPorPedidoComTrava(pedido())).isEmpty();
    }

    @Test
    void salvarDeNovoAtualizaOStatus() {
        PedidoId pedido = pedido();
        reservas().salvar(Reserva.reservada(pedido, itens(), T0, T0.plusSeconds(1800)));

        Reserva lida = reservas().buscarPorPedidoComTrava(pedido).orElseThrow();
        lida.confirmar(T0.plusSeconds(60));
        reservas().salvar(lida);

        assertThat(reservas().buscarPorPedido(pedido)).hasValueSatisfying(r -> {
            assertThat(r.status()).isEqualTo(StatusReserva.CONFIRMADA);
            assertThat(r.atualizadaEm()).isEqualTo(T0.plusSeconds(60));
        });
    }

    @Test
    void guardaTambemAReservaSemEstoque() {
        PedidoId pedido = pedido();
        List<ItemReservado> itens = itens();

        reservas().salvar(Reserva.indisponivel(pedido, itens, List.of(itens.get(0).produtoId()), T0));

        assertThat(reservas().buscarPorPedido(pedido)).hasValueSatisfying(
                r -> assertThat(r.status()).isEqualTo(StatusReserva.INDISPONIVEL));
    }
}
