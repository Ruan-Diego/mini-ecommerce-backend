package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.util.List;

/** Consulta os pedidos de um cliente. Pedido de outro cliente é "não encontrado", para não revelar que existe. */
public interface ConsultarPedidosUseCase {

    List<Pedido> meusPedidos(ClienteId cliente);

    Pedido obter(ClienteId cliente, PedidoId id);
}
