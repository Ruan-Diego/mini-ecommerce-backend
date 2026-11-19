package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.Dinheiro;

import java.util.Objects;

/**
 * Linha do carrinho. Guarda nome e preço unitário como estavam ao adicionar: o preço
 * só muda por {@code Carrinho.atualizarPreco} (evento PrecoAlterado do catálogo).
 *
 * {@code precoAnterior} é o aviso de mudança de preço: o valor que o cliente tinha visto antes de o
 * catálogo mudar o preço (nulo se o preço não mudou). Imutável: cada alteração devolve um item novo.
 */
public record ItemCarrinho(ProdutoId produtoId, String nome, Dinheiro precoUnitario, int quantidade,
                           Dinheiro precoAnterior) {

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
        if (precoAnterior != null && (!precoAnterior.ehPositivo() || precoAnterior.equals(precoUnitario))) {
            throw new RegraDeNegocioException("preço anterior deve ser positivo e diferente do atual: " + precoAnterior);
        }
    }

    /** Item sem aviso de mudança de preço (o caso normal ao adicionar ao carrinho). */
    public ItemCarrinho(ProdutoId produtoId, String nome, Dinheiro precoUnitario, int quantidade) {
        this(produtoId, nome, precoUnitario, quantidade, null);
    }

    public Dinheiro subtotal() {
        return precoUnitario.multiplicar(quantidade);
    }

    /** O preço mudou desde que o cliente viu o item? */
    public boolean precoAlterado() {
        return precoAnterior != null;
    }

    ItemCarrinho comQuantidade(int nova) {
        return new ItemCarrinho(produtoId, nome, precoUnitario, nova, precoAnterior);
    }

    /**
     * Aplica o preço novo do catálogo. O aviso guarda sempre o primeiro valor que o cliente viu: se o
     * preço mudar duas vezes antes de ele olhar, o aviso diz "de 100 para 120", e não "de 110 para 120".
     * Se o preço voltar ao valor que o cliente viu, o aviso some. Mesmo preço: nada muda.
     */
    ItemCarrinho comPreco(Dinheiro novo) {
        if (novo.equals(precoUnitario)) {
            return this;
        }
        Dinheiro visto = precoAnterior != null ? precoAnterior : precoUnitario;
        return new ItemCarrinho(produtoId, nome, novo, quantidade, novo.equals(visto) ? null : visto);
    }
}
