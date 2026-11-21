package com.ruandiego.mini.estoque.domain.model;

import com.ruandiego.mini.estoque.domain.RegraDeNegocioException;

import java.util.Objects;

/**
 * Saldo de um produto: `disponivel` pode ser vendido; `reservado` está separado para pedidos que ainda não
 * foram pagos. Reservar move do disponível para o reservado; liberar devolve; a baixa definitiva tira do
 * reservado (o produto saiu do estoque). Nenhum dos dois saldos fica negativo: cada operação confere antes de mexer.
 */
public class ItemEstoque {

    /** Abaixo disso o produto aparece no painel do admin como estoque baixo. */
    public static final int LIMITE_ESTOQUE_BAIXO = 5;

    private final ProdutoId produtoId;
    private int disponivel;
    private int reservado;

    private ItemEstoque(ProdutoId produtoId, int disponivel, int reservado) {
        this.produtoId = Objects.requireNonNull(produtoId, "produtoId");
        if (disponivel < 0 || reservado < 0) {
            throw new RegraDeNegocioException("o estoque não pode ser negativo");
        }
        this.disponivel = disponivel;
        this.reservado = reservado;
    }

    public static ItemEstoque novo(ProdutoId produtoId, int disponivel) {
        return new ItemEstoque(produtoId, disponivel, 0);
    }

    public static ItemEstoque reconstituir(ProdutoId produtoId, int disponivel, int reservado) {
        return new ItemEstoque(produtoId, disponivel, reservado);
    }

    /** Estoque baixo olha só o que ainda pode ser vendido; o reservado já tem dono. */
    public boolean estoqueBaixo() {
        return disponivel < LIMITE_ESTOQUE_BAIXO;
    }

    public boolean podeReservar(int quantidade) {
        return quantidade > 0 && quantidade <= disponivel;
    }

    public void reservar(int quantidade) {
        exigirPositiva(quantidade);
        if (quantidade > disponivel) {
            throw new RegraDeNegocioException("estoque insuficiente do produto " + produtoId
                    + ": pedido " + quantidade + ", disponível " + disponivel);
        }
        disponivel -= quantidade;
        reservado += quantidade;
    }

    public void liberar(int quantidade) {
        exigirPositiva(quantidade);
        exigirReservado(quantidade);
        reservado -= quantidade;
        disponivel += quantidade;
    }

    public void baixar(int quantidade) {
        exigirPositiva(quantidade);
        exigirReservado(quantidade);
        reservado -= quantidade;
    }

    /** Ajuste do administrador: chegou mercadoria. Soma ao saldo vendável. */
    public void entrada(int quantidade) {
        exigirPositiva(quantidade);
        disponivel = Math.addExact(disponivel, quantidade);
    }

    /** Ajuste do administrador: mercadoria perdida ou danificada. Só pode tirar o que ainda está disponível. */
    public void perda(int quantidade) {
        exigirPositiva(quantidade);
        if (quantidade > disponivel) {
            throw new RegraDeNegocioException("a perda de " + quantidade + " é maior que o disponível (" + disponivel + ")");
        }
        disponivel -= quantidade;
    }

    /** Ajuste do administrador: define o saldo vendável. O que já está reservado não muda. */
    public void definirDisponivel(int novo) {
        if (novo < 0) {
            throw new RegraDeNegocioException("o estoque disponível não pode ser negativo");
        }
        this.disponivel = novo;
    }

    private void exigirPositiva(int quantidade) {
        if (quantidade < 1) {
            throw new RegraDeNegocioException("a quantidade deve ser maior que zero");
        }
    }

    private void exigirReservado(int quantidade) {
        if (quantidade > reservado) {
            throw new RegraDeNegocioException("o produto " + produtoId + " tem só " + reservado + " reservado(s)");
        }
    }

    public ProdutoId produtoId() {
        return produtoId;
    }

    public int disponivel() {
        return disponivel;
    }

    public int reservado() {
        return reservado;
    }
}
