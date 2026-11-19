package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/** Linha da tabela item_carrinho. É @Embeddable porque não tem identidade própria: pertence ao carrinho. */
@Embeddable
class ItemCarrinhoJpa {

    @Column(name = "produto_id", nullable = false, columnDefinition = "char(36)")
    private String produtoId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false)
    private int quantidade;

    protected ItemCarrinhoJpa() {
        // exigido pelo JPA
    }

    ItemCarrinhoJpa(String produtoId, String nome, BigDecimal precoUnitario, int quantidade) {
        this.produtoId = produtoId;
        this.nome = nome;
        this.precoUnitario = precoUnitario;
        this.quantidade = quantidade;
    }

    String getProdutoId() {
        return produtoId;
    }

    String getNome() {
        return nome;
    }

    BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    int getQuantidade() {
        return quantidade;
    }
}
