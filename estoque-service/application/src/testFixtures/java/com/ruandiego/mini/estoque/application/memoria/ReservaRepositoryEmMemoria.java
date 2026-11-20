package com.ruandiego.mini.estoque.application.memoria;

import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.Reserva;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória: guarda cópias (reconstituídas, sem eventos), como o banco. */
public class ReservaRepositoryEmMemoria implements ReservaRepository {

    private final Map<PedidoId, Reserva> reservas = new HashMap<>();

    @Override
    public Optional<Reserva> buscarPorPedido(PedidoId pedido) {
        return Optional.ofNullable(reservas.get(pedido)).map(ReservaRepositoryEmMemoria::copia);
    }

    @Override
    public Optional<Reserva> buscarPorPedidoComTrava(PedidoId pedido) {
        return buscarPorPedido(pedido);
    }

    @Override
    public void salvar(Reserva reserva) {
        reservas.put(reserva.pedidoId(), copia(reserva));
    }

    public void limpar() {
        reservas.clear();
    }

    private static Reserva copia(Reserva r) {
        return Reserva.reconstituir(r.pedidoId(), r.itens(), r.status(), r.criadaEm(), r.expiraEm(), r.atualizadaEm());
    }
}
