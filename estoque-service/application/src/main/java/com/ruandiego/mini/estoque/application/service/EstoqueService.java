package com.ruandiego.mini.estoque.application.service;

import com.ruandiego.mini.estoque.application.NaoEncontradoException;
import com.ruandiego.mini.estoque.application.port.in.AjustarEstoqueUseCase;
import com.ruandiego.mini.estoque.application.port.in.ConsultarEstoqueUseCase;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase;
import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.estoque.application.port.out.PublicadorEventos;
import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import com.ruandiego.mini.estoque.domain.model.Reserva;
import com.ruandiego.mini.estoque.domain.model.StatusReserva;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Os casos de uso do estoque. Sem Spring: a transação é aplicada pelo bootstrap em volta de cada método, então
 * o saldo, a reserva, o registro da mensagem como tratada e os eventos da outbox são gravados juntos ou nenhum.
 *
 * Por que o saldo nunca fica negativo, mesmo com pedidos simultâneos: a reserva trava os itens
 * ({@link ItemEstoqueRepository#buscarComTrava}) antes de ler o saldo. A segunda transação espera a primeira
 * terminar e então lê o saldo já descontado. O domínio ainda confere antes de cada mudança, e o banco tem
 * CHECK (disponivel >= 0) como última barreira.
 */
public class EstoqueService implements ProcessarEventoDePedidoUseCase, ConsultarEstoqueUseCase, AjustarEstoqueUseCase {

    /** Nome deste consumidor no registro de mensagens tratadas. */
    public static final String CONSUMIDOR = "estoque-service";

    /** Quanto tempo uma reserva vale sem pagamento (o job de expiração do item 44 usa esse prazo). */
    public static final Duration VALIDADE_DA_RESERVA = Duration.ofMinutes(30);

    private final ItemEstoqueRepository itens;
    private final ReservaRepository reservas;
    private final MensagensProcessadas processadas;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public EstoqueService(ItemEstoqueRepository itens, ReservaRepository reservas, MensagensProcessadas processadas,
                          PublicadorEventos publicador, Clock relogio) {
        this.itens = itens;
        this.reservas = reservas;
        this.processadas = processadas;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    @Override
    public void criado(PedidoCriado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        if (reservas.buscarPorPedido(evento.pedido()).isPresent()) {
            return; // este pedido já foi tratado (mensagem repetida com outro id)
        }
        Instant agora = relogio.instant();
        List<ItemReservado> pedidos = Reserva.consolidar(evento.itens());
        Map<ProdutoId, ItemEstoque> saldos = saldosTravados(pedidos);

        List<ProdutoId> semEstoque = new ArrayList<>();
        for (ItemReservado pedido : pedidos) {
            ItemEstoque saldo = saldos.get(pedido.produtoId());
            if (saldo == null || !saldo.podeReservar(pedido.quantidade())) {
                semEstoque.add(pedido.produtoId());
            }
        }

        Reserva reserva;
        if (semEstoque.isEmpty()) {
            for (ItemReservado pedido : pedidos) {
                ItemEstoque saldo = saldos.get(pedido.produtoId());
                saldo.reservar(pedido.quantidade());
                itens.salvar(saldo);
            }
            reserva = Reserva.reservada(evento.pedido(), pedidos, agora, agora.plus(VALIDADE_DA_RESERVA));
        } else {
            // Tudo ou nada: se um item falta, nenhum é separado.
            reserva = Reserva.indisponivel(evento.pedido(), pedidos, semEstoque, agora);
        }
        salvarEPublicar(reserva);
    }

    @Override
    public void pago(PedidoPago evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Reserva reserva = reserva(evento.pedido());
        if (reserva.status() != StatusReserva.RESERVADA) {
            return; // já confirmada (outra mensagem) ou já liberada: nada a fazer
        }
        Map<ProdutoId, ItemEstoque> saldos = saldosTravados(reserva.itens());
        for (ItemReservado item : reserva.itens()) {
            ItemEstoque saldo = saldos.get(item.produtoId());
            saldo.baixar(item.quantidade());
            itens.salvar(saldo);
        }
        reserva.confirmar(relogio.instant());
        salvarEPublicar(reserva);
    }

    @Override
    public void cancelado(PedidoCancelado evento) {
        if (!processadas.registrar(CONSUMIDOR, evento.mensagemId())) {
            return;
        }
        Reserva reserva = reserva(evento.pedido());
        if (reserva.status() != StatusReserva.RESERVADA) {
            // Sem estoque (nada foi separado), já liberada, ou paga (a baixa é definitiva e não volta sozinha).
            return;
        }
        Map<ProdutoId, ItemEstoque> saldos = saldosTravados(reserva.itens());
        for (ItemReservado item : reserva.itens()) {
            ItemEstoque saldo = saldos.get(item.produtoId());
            saldo.liberar(item.quantidade());
            itens.salvar(saldo);
        }
        reserva.liberar(relogio.instant());
        salvarEPublicar(reserva);
    }

    @Override
    public ItemEstoque consultar(ProdutoId produto) {
        return itens.buscar(produto)
                .orElseThrow(() -> new NaoEncontradoException("produto " + produto + " sem estoque cadastrado"));
    }

    @Override
    public ItemEstoque definirDisponivel(ProdutoId produto, int disponivel) {
        ItemEstoque item = itens.buscarComTrava(List.of(produto)).stream().findFirst()
                .orElseGet(() -> ItemEstoque.novo(produto, 0));
        item.definirDisponivel(disponivel);
        itens.salvar(item);
        return item;
    }

    private Reserva reserva(PedidoId pedido) {
        return reservas.buscarPorPedidoComTrava(pedido)
                .orElseThrow(() -> new NaoEncontradoException("reserva do pedido " + pedido + " não encontrada"));
    }

    private Map<ProdutoId, ItemEstoque> saldosTravados(List<ItemReservado> pedidos) {
        Map<ProdutoId, ItemEstoque> saldos = new HashMap<>();
        itens.buscarComTrava(pedidos.stream().map(ItemReservado::produtoId).toList())
                .forEach(saldo -> saldos.put(saldo.produtoId(), saldo));
        return saldos;
    }

    private void salvarEPublicar(Reserva reserva) {
        reservas.salvar(reserva);
        publicador.publicar(reserva.eventosPendentes());
        reserva.limparEventos();
    }
}
