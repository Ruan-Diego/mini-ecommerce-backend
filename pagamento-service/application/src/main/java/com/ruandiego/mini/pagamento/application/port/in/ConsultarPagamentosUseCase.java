package com.ruandiego.mini.pagamento.application.port.in;

import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.util.List;

/** Consulta os pagamentos de um cliente. Pagamento de outro cliente é "não encontrado", para não revelar que existe. */
public interface ConsultarPagamentosUseCase {

    Pagamento obter(ClienteId cliente, PagamentoId id);

    /** Pagamentos do pedido, do mais recente para o mais antigo. Vazio se o pedido não é do cliente ou não tem pagamento. */
    List<Pagamento> doPedido(ClienteId cliente, PedidoId pedido);
}
