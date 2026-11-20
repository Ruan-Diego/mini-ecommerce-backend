package com.ruandiego.mini.estoque.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Linha de item_estoque. Entidade JPA, separada do ItemEstoque do domínio (o mapeamento é do adapter). */
@Entity
@Table(name = "item_estoque")
class ItemEstoqueJpa {

    @Id
    @Column(name = "produto_id", columnDefinition = "char(36)", nullable = false, updatable = false)
    private String produtoId;

    @Column(nullable = false)
    private int disponivel;

    @Column(nullable = false)
    private int reservado;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant atualizadoEm;

    protected ItemEstoqueJpa() {
        // exigido pelo JPA
    }

    ItemEstoqueJpa(String produtoId) {
        this.produtoId = produtoId;
    }

    void preencher(int disponivel, int reservado, Instant atualizadoEm) {
        this.disponivel = disponivel;
        this.reservado = reservado;
        this.atualizadoEm = atualizadoEm;
    }

    String getProdutoId() {
        return produtoId;
    }

    int getDisponivel() {
        return disponivel;
    }

    int getReservado() {
        return reservado;
    }
}
