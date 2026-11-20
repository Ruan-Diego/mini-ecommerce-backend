package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapter real do PedidoRepository: MySQL via JPA. A transação é a do use case (aberta no wiring). */
@Component
public class PedidoRepositoryJpa implements PedidoRepository {

    private final PedidoJpaRepository jpa;

    PedidoRepositoryJpa(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Pedido pedido) {
        // Carrega a linha existente (se houver) para o Hibernate gerar só o UPDATE do que mudou.
        PedidoJpa linha = jpa.findById(pedido.id().toString()).orElseGet(() -> new PedidoJpa(pedido.id().toString()));
        PedidoMapper.paraJpa(pedido, linha);
        jpa.save(linha);
    }

    @Override
    public Optional<Pedido> buscarPorId(PedidoId id) {
        return jpa.findById(id.toString()).map(PedidoMapper::paraDominio);
    }

    @Override
    public List<Pedido> listarPorCliente(ClienteId cliente) {
        return jpa.findByClienteIdOrderByCriadoEmDesc(cliente.valor()).stream().map(PedidoMapper::paraDominio).toList();
    }
}
