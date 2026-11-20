package com.ruandiego.mini.estoque.adapters.out.persistencia;

import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import com.ruandiego.mini.estoque.domain.model.Reserva;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapter real do ReservaRepository: MySQL via JPA. A transação é a do use case (aberta no wiring). */
@Component
public class ReservaRepositoryJpa implements ReservaRepository {

    private final ReservaJpaRepository jpa;

    ReservaRepositoryJpa(ReservaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Reserva> buscarPorPedido(PedidoId pedido) {
        return jpa.findById(pedido.toString()).map(ReservaRepositoryJpa::paraDominio);
    }

    @Override
    public Optional<Reserva> buscarPorPedidoComTrava(PedidoId pedido) {
        return jpa.buscarComTrava(pedido.toString()).map(ReservaRepositoryJpa::paraDominio);
    }

    @Override
    public void salvar(Reserva reserva) {
        // Existe: só o status muda (os itens são fixos). Não existe: cria a reserva com os itens.
        ReservaJpa existente = jpa.findById(reserva.pedidoId().toString()).orElse(null);
        if (existente != null) {
            existente.atualizar(reserva.status(), reserva.atualizadaEm());
            jpa.save(existente);
            return;
        }
        jpa.save(new ReservaJpa(reserva.pedidoId().toString(), reserva.status(), reserva.criadaEm(),
                reserva.expiraEm(), reserva.atualizadaEm(),
                reserva.itens().stream()
                        .map(i -> new ReservaJpa.ItemJpa(i.produtoId().toString(), i.quantidade()))
                        .toList()));
    }

    private static Reserva paraDominio(ReservaJpa linha) {
        return Reserva.reconstituir(PedidoId.de(linha.getPedidoId()),
                linha.getItens().stream()
                        .map(i -> new ItemReservado(ProdutoId.de(i.getProdutoId()), i.getQuantidade())).toList(),
                linha.getStatus(), linha.getCriadaEm(), linha.getExpiraEm(), linha.getAtualizadaEm());
    }
}
