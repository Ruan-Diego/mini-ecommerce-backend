package com.ruandiego.mini.pedido.application.port.in;

/**
 * Cancela os pedidos que ficaram 30 minutos sem pagamento. Quem chama é o job agendado (adapter de entrada no
 * bootstrap), não um cliente: por isso não é o {@link CancelarPedidoUseCase}, que exige o dono do pedido.
 * O cancelamento publica `pedido.cancelado`, que faz o estoque devolver a reserva e o pagamento estornar.
 */
public interface ExpirarPedidosSemPagamentoUseCase {

    /** @return quantos pedidos foram cancelados nesta rodada */
    int expirarVencidos();
}
