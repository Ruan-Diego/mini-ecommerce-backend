package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;
import java.util.List;

/**
 * Não havia estoque para o pedido: nada foi separado. `produtosSemEstoque` lista os produtos que faltaram
 * (o pedido-service, na saga do item 43, usa o evento para cancelar o pedido). Só tipos simples.
 */
public record EstoqueIndisponivel(String pedidoId, List<String> produtosSemEstoque, Instant ocorridoEm)
        implements DomainEvent {

    public EstoqueIndisponivel {
        produtosSemEstoque = List.copyOf(produtosSemEstoque);
    }

    @Override
    public String tipo() {
        return "estoque.indisponivel";
    }
}
