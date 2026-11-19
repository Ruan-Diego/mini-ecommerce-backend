package com.ruandiego.mini.carrinho.adapters.out.visitante;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.CarrinhoId;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ItemCarrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Formato do carrinho dentro do Redis. O domínio não tem anotações de Jackson, então o adapter tem o
 * seu próprio formato e converte nas duas direções. Datas e preços vão como texto, para o JSON não
 * depender de módulos nem de configuração do ObjectMapper. Campo novo no futuro: ignorar os
 * desconhecidos deixa um carrinho gravado por versão mais nova ainda legível.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CarrinhoJson(String id, List<ItemJson> itens, CupomJson cupom) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemJson(String produtoId, String nome, String precoUnitario, int quantidade) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CupomJson(String codigo, String tipo, String valor, String validoAte, String valorMinimo) {
    }

    static CarrinhoJson de(Carrinho carrinho) {
        Cupom cupom = carrinho.cupom();
        return new CarrinhoJson(carrinho.id().toString(),
                carrinho.itens().stream()
                        .map(i -> new ItemJson(i.produtoId().toString(), i.nome(),
                                i.precoUnitario().valor().toPlainString(), i.quantidade()))
                        .toList(),
                cupom == null ? null
                        : new CupomJson(cupom.codigo(), cupom.tipo().name(), cupom.valor().toPlainString(),
                                cupom.validoAte().toString(), cupom.valorMinimo().valor().toPlainString()));
    }

    Carrinho paraDominio() {
        Cupom dominioCupom = cupom == null ? null
                : new Cupom(cupom.codigo(), Cupom.Tipo.valueOf(cupom.tipo()), new BigDecimal(cupom.valor()),
                        Instant.parse(cupom.validoAte()), new Dinheiro(new BigDecimal(cupom.valorMinimo())));
        return Carrinho.reconstituir(CarrinhoId.de(id),
                itens.stream()
                        .map(i -> new ItemCarrinho(ProdutoId.de(i.produtoId()), i.nome(),
                                new Dinheiro(new BigDecimal(i.precoUnitario())), i.quantidade()))
                        .toList(),
                dominioCupom);
    }
}
