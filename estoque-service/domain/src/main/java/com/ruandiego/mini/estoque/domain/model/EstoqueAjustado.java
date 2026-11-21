package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;

/**
 * O admin ajustou o saldo vendável de um produto. É o registro do motivo (`ajuste`: ENTRADA, PERDA ou CORRECAO) e do antes/depois. Só tipos
 * simples: o evento vira JSON na outbox.
 */
public record EstoqueAjustado(String produtoId, String ajuste, int quantidade, int disponivelAntes, int disponivelDepois,
                              Instant ocorridoEm) implements DomainEvent {

    @Override
    public String tipo() {
        return "estoque.ajustado";
    }
}
