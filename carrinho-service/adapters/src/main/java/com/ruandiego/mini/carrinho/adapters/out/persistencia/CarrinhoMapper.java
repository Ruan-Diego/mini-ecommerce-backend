package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.CarrinhoId;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ItemCarrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

/**
 * Traduz entre o domínio e as entidades JPA. Entidade de domínio não é entidade JPA: o domínio não
 * conhece anotações, e a tabela pode mudar sem mexer nas regras. O mapper é o único lugar que conhece os dois.
 */
final class CarrinhoMapper {

    private CarrinhoMapper() {
    }

    static CarrinhoJpa paraJpa(ClienteId cliente, Carrinho carrinho) {
        return new CarrinhoJpa(carrinho.id().toString(), cliente.valor(), carrinho.cupom(),
                carrinho.itens().stream()
                        .map(i -> new ItemCarrinhoJpa(i.produtoId().toString(), i.nome(), i.precoUnitario().valor(),
                                i.quantidade(), i.precoAnterior() == null ? null : i.precoAnterior().valor()))
                        .toList());
    }

    static Carrinho paraDominio(CarrinhoJpa jpa) {
        Cupom cupom = jpa.getCupomCodigo() == null ? null
                : new Cupom(jpa.getCupomCodigo(), jpa.getCupomTipo(), jpa.getCupomValor(), jpa.getCupomValidoAte(),
                        new Dinheiro(jpa.getCupomValorMinimo()));
        return Carrinho.reconstituir(CarrinhoId.de(jpa.getId()),
                jpa.getItens().stream()
                        .map(i -> new ItemCarrinho(ProdutoId.de(i.getProdutoId()), i.getNome(),
                                new Dinheiro(i.getPrecoUnitario()), i.getQuantidade(),
                                i.getPrecoAnterior() == null ? null : new Dinheiro(i.getPrecoAnterior())))
                        .toList(),
                cupom);
    }

    static Cupom paraDominio(CupomJpa jpa) {
        return new Cupom(jpa.getCodigo(), jpa.getTipo(), jpa.getValor(), jpa.getValidoAte(),
                new Dinheiro(jpa.getValorMinimo()));
    }
}
