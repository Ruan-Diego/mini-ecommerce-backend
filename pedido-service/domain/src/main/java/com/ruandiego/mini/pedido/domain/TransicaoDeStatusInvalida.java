package com.ruandiego.mini.pedido.domain;

import com.ruandiego.mini.pedido.domain.model.StatusPedido;

/**
 * Tentativa de levar o pedido para um status que o fluxo não permite (ex.: enviar um pedido que
 * ainda aguarda pagamento, ou cancelar um pedido já enviado). É uma regra de negócio: vira 422 no REST.
 */
public class TransicaoDeStatusInvalida extends RegraDeNegocioException {

    private final StatusPedido de;
    private final StatusPedido para;

    public TransicaoDeStatusInvalida(StatusPedido de, StatusPedido para) {
        super("transição inválida: " + de + " → " + para);
        this.de = de;
        this.para = para;
    }

    public StatusPedido de() {
        return de;
    }

    public StatusPedido para() {
        return para;
    }
}
