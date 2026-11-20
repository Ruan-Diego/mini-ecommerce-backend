package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.domain.model.Frete;

import java.math.BigDecimal;

/** Uma opção de frete na resposta da cotação e dentro do pedido. */
record FreteResposta(String codigo, String nome, BigDecimal valor, int prazoDias) {

    static FreteResposta de(Frete frete) {
        return new FreteResposta(frete.codigo(), frete.nome(), frete.valor().valor(), frete.prazoDias());
    }
}
