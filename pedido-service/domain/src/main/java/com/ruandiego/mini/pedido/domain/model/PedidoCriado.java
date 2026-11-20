package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * O pedido foi criado e aguarda pagamento. Só tipos simples (texto, número, data): o evento vira JSON
 * na outbox e é lido por outros serviços, que não conhecem os value objects deste domínio.
 *
 * `itens` (produto e quantidade) existe para o estoque-service reservar o que foi pedido. Campo novo
 * em evento é compatível: quem já consome (o carrinho) ignora o que não conhece.
 */
public record PedidoCriado(String pedidoId, String clienteId, BigDecimal total, List<Item> itens, Instant ocorridoEm)
        implements DomainEvent {

    public record Item(String produtoId, int quantidade) {
    }

    public PedidoCriado {
        itens = itens == null ? List.of() : List.copyOf(itens);
    }

    /** Sem itens (usado só por quem não precisa deles, como testes de outbox). */
    public PedidoCriado(String pedidoId, String clienteId, BigDecimal total, Instant ocorridoEm) {
        this(pedidoId, clienteId, total, List.of(), ocorridoEm);
    }

    @Override
    public String tipo() {
        return "pedido.criado";
    }
}
