package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.estoque.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reserva do estoque de um pedido. É identificada pelo pedido (no máximo uma por pedido) e guarda os itens
 * separados e até quando vale (`expiraEm`: o job do item 44 cancela o pedido sem pagamento nesse prazo).
 * Registra o evento de cada mudança de estado, para publicação depois da persistência.
 */
public class Reserva extends AggregateRoot {

    private final PedidoId pedidoId;
    private final List<ItemReservado> itens;
    private final Instant criadaEm;
    private final Instant expiraEm;
    private StatusReserva status;
    private Instant atualizadaEm;

    private Reserva(PedidoId pedidoId, List<ItemReservado> itens, StatusReserva status, Instant criadaEm,
                    Instant expiraEm, Instant atualizadaEm) {
        this.pedidoId = Objects.requireNonNull(pedidoId, "pedidoId");
        this.itens = List.copyOf(itens);
        this.status = Objects.requireNonNull(status, "status");
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm");
        this.atualizadaEm = Objects.requireNonNull(atualizadaEm, "atualizadaEm");
    }

    /** Reserva feita: os itens já foram separados no saldo. Registra {@link EstoqueReservado}. */
    public static Reserva reservada(PedidoId pedidoId, List<ItemReservado> itens, Instant agora, Instant expiraEm) {
        Reserva reserva = new Reserva(pedidoId, consolidar(itens), StatusReserva.RESERVADA, agora, expiraEm, agora);
        reserva.registrar(new EstoqueReservado(pedidoId.toString(), agora));
        return reserva;
    }

    /** Pedido sem estoque: nada foi separado. Registra {@link EstoqueIndisponivel}. */
    public static Reserva indisponivel(PedidoId pedidoId, List<ItemReservado> itens, List<ProdutoId> semEstoque,
                                       Instant agora) {
        Reserva reserva = new Reserva(pedidoId, consolidar(itens), StatusReserva.INDISPONIVEL, agora, agora, agora);
        reserva.registrar(new EstoqueIndisponivel(pedidoId.toString(),
                semEstoque.stream().map(ProdutoId::toString).toList(), agora));
        return reserva;
    }

    public static Reserva reconstituir(PedidoId pedidoId, List<ItemReservado> itens, StatusReserva status,
                                       Instant criadaEm, Instant expiraEm, Instant atualizadaEm) {
        return new Reserva(pedidoId, itens, status, criadaEm, expiraEm, atualizadaEm);
    }

    /** Baixa definitiva (o pedido foi pago). Só vale para uma reserva ainda RESERVADA. Registra {@link EstoqueBaixado}. */
    public void confirmar(Instant agora) {
        exigirReservada("confirmar");
        status = StatusReserva.CONFIRMADA;
        atualizadaEm = agora;
        registrar(new EstoqueBaixado(pedidoId.toString(), agora));
    }

    /** Devolve o estoque (o pedido foi cancelado). Só vale para uma reserva ainda RESERVADA. Registra {@link EstoqueLiberado}. */
    public void liberar(Instant agora) {
        exigirReservada("liberar");
        status = StatusReserva.LIBERADA;
        atualizadaEm = agora;
        registrar(new EstoqueLiberado(pedidoId.toString(), agora));
    }

    private void exigirReservada(String acao) {
        if (status != StatusReserva.RESERVADA) {
            throw new RegraDeNegocioException("não é possível " + acao + " a reserva do pedido " + pedidoId
                    + ": ela está " + status);
        }
    }

    /** Mesmo produto repetido vira uma linha só, na ordem em que apareceu. */
    public static List<ItemReservado> consolidar(List<ItemReservado> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("a reserva precisa de pelo menos um item");
        }
        Map<ProdutoId, Integer> somados = new LinkedHashMap<>();
        for (ItemReservado item : itens) {
            somados.merge(item.produtoId(), item.quantidade(), Integer::sum);
        }
        List<ItemReservado> resultado = new ArrayList<>();
        somados.forEach((produto, quantidade) -> resultado.add(new ItemReservado(produto, quantidade)));
        return resultado;
    }

    public PedidoId pedidoId() {
        return pedidoId;
    }

    public List<ItemReservado> itens() {
        return itens;
    }

    public StatusReserva status() {
        return status;
    }

    public Instant criadaEm() {
        return criadaEm;
    }

    public Instant expiraEm() {
        return expiraEm;
    }

    public Instant atualizadaEm() {
        return atualizadaEm;
    }
}
