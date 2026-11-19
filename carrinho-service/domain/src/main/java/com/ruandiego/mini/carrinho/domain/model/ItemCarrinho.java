package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.Objects;

/**
 * Linha do carrinho. Guarda nome e preço unitário como estavam ao adicionar: o preço
 * só muda por {@code Carrinho.atualizarPreco} (evento PrecoAlterado do catálogo).
 * Imutável: cada alteração devolve um item novo.
 */
public record ItemCarrinho(ProdutoId produtoId, String nome, Dinheiro precoUnitario, int quantidade) {

    public static final int QUANTIDADE_MINIMA = 1;
    public static final int QUANTIDADE_MAXIMA = 10;

    public ItemCarrinho {
        Objects.requireNonNull(produtoId, "produtoId");
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("nome do item é obrigatório");
        }
        nome = nome.trim();
        if (precoUnitario == null || !precoUnitario.ehPositivo()) {
            throw new RegraDeNegocioException("preço unitário deve ser maior que zero: " + precoUnitario);
        }
        if (quantidade < QUANTIDADE_MINIMA || quantidade > QUANTIDADE_MAXIMA) {
            throw new RegraDeNegocioException("quantidade deve ficar entre " + QUANTIDADE_MINIMA + " e "
                    + QUANTIDADE_MAXIMA + " por item: " + quantidade);
        }
    }

    public Dinheiro subtotal() {
        return precoUnitario.multiplicar(quantidade);
    }

    ItemCarrinho comQuantidade(int nova) {
        return new ItemCarrinho(produtoId, nome, precoUnitario, nova);
    }

    ItemCarrinho comPreco(Dinheiro novo) {
        return new ItemCarrinho(produtoId, nome, novo, quantidade);
    }
}
