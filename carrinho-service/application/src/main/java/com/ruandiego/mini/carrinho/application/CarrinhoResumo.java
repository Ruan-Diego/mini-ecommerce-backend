package com.ruandiego.mini.carrinho.application;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ItemCarrinho;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;
import java.util.List;

/**
 * Foto do carrinho para quem consome o use case: itens, cupom e os valores já calculados no instante
 * da consulta. O agregado não sai da application; o adapter REST só formata este resumo.
 */
public record CarrinhoResumo(List<ItemCarrinho> itens, int quantidadeTotal, Dinheiro subtotal, Cupom cupom,
                             Dinheiro desconto, Dinheiro total) {

    public static CarrinhoResumo de(Carrinho carrinho, Instant agora) {
        return new CarrinhoResumo(carrinho.itens(), carrinho.quantidadeTotal(), carrinho.subtotal(),
                carrinho.cupom(), carrinho.desconto(agora), carrinho.total(agora));
    }
}
