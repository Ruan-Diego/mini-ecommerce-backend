package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ItemCarrinho;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Formato JSON do carrinho. O agregado nunca vai direto para o JSON: assim o domínio pode mudar
 * sem quebrar o contrato da API. Valores em reais, com 2 casas.
 */
public record CarrinhoResposta(List<ItemResposta> itens, int quantidadeTotal, BigDecimal subtotal,
                               CupomResposta cupom, BigDecimal desconto, BigDecimal total) {

    /**
     * {@code precoAlterado} e {@code precoAnterior} são o aviso de mudança de preço: o valor que o cliente
     * tinha visto (nulo quando o preço não mudou) e o valor atual está em {@code precoUnitario}.
     */
    public record ItemResposta(String produtoId, String nome, BigDecimal precoUnitario, int quantidade,
                               BigDecimal subtotal, boolean precoAlterado, BigDecimal precoAnterior) {

        static ItemResposta de(ItemCarrinho item) {
            return new ItemResposta(item.produtoId().toString(), item.nome(), item.precoUnitario().valor(),
                    item.quantidade(), item.subtotal().valor(), item.precoAlterado(),
                    item.precoAnterior() == null ? null : item.precoAnterior().valor());
        }
    }

    public record CupomResposta(String codigo, String tipo, BigDecimal valor, Instant validoAte,
                                BigDecimal valorMinimo) {

        static CupomResposta de(Cupom cupom) {
            return new CupomResposta(cupom.codigo(), cupom.tipo().name(), cupom.valor(), cupom.validoAte(),
                    cupom.valorMinimo().valor());
        }
    }

    static CarrinhoResposta de(CarrinhoResumo resumo) {
        return new CarrinhoResposta(resumo.itens().stream().map(ItemResposta::de).toList(),
                resumo.quantidadeTotal(), resumo.subtotal().valor(),
                resumo.cupom() == null ? null : CupomResposta.de(resumo.cupom()),
                resumo.desconto().valor(), resumo.total().valor());
    }
}
