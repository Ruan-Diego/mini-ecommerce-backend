package com.ruandiego.mini.estoque.domain.model;

/**
 * RESERVADA: separada, esperando o pagamento. CONFIRMADA: o pedido foi pago e a baixa é definitiva.
 * LIBERADA: o pedido foi cancelado e o estoque voltou. INDISPONIVEL: não havia estoque, nada foi separado
 * (a linha existe para o mesmo pedido não ser reservado de novo se a mensagem repetir).
 */
public enum StatusReserva {
    RESERVADA, CONFIRMADA, LIBERADA, INDISPONIVEL
}
