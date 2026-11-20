package com.ruandiego.mini.pedido.application.port.out;

import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.util.List;
import java.util.Optional;

/** Persistência do agregado Pedido. Salvar cria ou atualiza (o status muda com o tempo). */
public interface PedidoRepository {

    void salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(PedidoId id);

    /** Do mais recente para o mais antigo. */
    List<Pedido> listarPorCliente(ClienteId cliente);
}
