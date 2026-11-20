package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

/** O cliente cancela o próprio pedido. A regra de quais status permitem cancelar é do domínio. */
public interface CancelarPedidoUseCase {

    Pedido cancelar(ClienteId cliente, PedidoId id);
}
